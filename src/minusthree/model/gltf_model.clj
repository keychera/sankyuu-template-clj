(ns minusthree.model.gltf-model
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [fastmath.matrix :as mat :refer [mat->float-array]]
   [fastmath.quaternion :as q]
   [fastmath.vector :as v]
   [minusthree.anime.bones :as bones]
   [minusthree.engine.camera :as camera]
   [minusthree.engine.geom :as geom]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.macros :refer [s->]]
   [minusthree.engine.math :refer [decompose-Mat4x4 quat->mat4 scaling-mat
                                   translation-mat]]
   [minusthree.engine.utils :as utils]
   [minusthree.engine.world :as world]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.gl.shader :as shader]
   [minusthree.gl.texture :as texture]
   [minusthree.model.model-rendering :as model-rendering]
   [odoyle.rules :as o])
  (:import
   [java.nio ByteOrder]
   [org.lwjgl.opengl GL45]))

(def gltf-type->num-of-component
  {"SCALAR" 1
   "VEC2"   2
   "VEC3"   3
   "VEC4"   4
   "MAT2"   4
   "MAT3"   9
   "MAT4"   16})

(s/def ::data map?)
(s/def ::bins vector?)
(s/def ::primitives sequential?)

(defn create-vao-names [prefix]
  (map-indexed
   (fn [idx primitive]
     (let [vao-name (format "%s_vao%04d" prefix idx)]
       (assoc primitive :vao-name vao-name)))))

(defn match-textures [materials textures images]
  (map
   (fn [{:keys [material] :as primitive}]
     (let [material (get materials material)
           tex-idx  (some-> material :pbrMetallicRoughness :baseColorTexture :index)
           texture  (some->> tex-idx (get textures))
           image    (some->> texture :source (nth images))]
       (cond-> primitive
         image (assoc :tex-name (:tex-name image)))))))

(defn primitive-spell [gltf-data result-bin use-shader]
  (let [accessors   (some-> gltf-data :accessors)
        bufferViews (some-> gltf-data :bufferViews)]
    (map
     (fn [{:keys [vao-name attributes indices]}]
       [{:bind-vao vao-name}
        {:bind-current-buffer true}
        (eduction
         (map (fn [[attr-name accessor]]
                (merge {:attr-name attr-name}
                       (get accessors accessor))))
         (map (fn [{:keys [attr-name bufferView byteOffset componentType type]}]
                (let [bufferView (get bufferViews bufferView)]
                  {:point-attr (keyword attr-name)
                   :use-shader use-shader
                   :count (gltf-type->num-of-component type)
                   :component-type componentType
                   :offset (+ (:byteOffset bufferView) byteOffset)})))
         attributes)

        (let [id-accessor   (get accessors indices)
              id-bufferView (get bufferViews (:bufferView id-accessor))
              id-byteOffset (:byteOffset id-bufferView)
              id-byteLength (:byteLength id-bufferView)]
          {:buffer-type GL45/GL_ELEMENT_ARRAY_BUFFER
           :buffer-data (let [slice (doto (.duplicate result-bin)
                                      (.position id-byteOffset)
                                      (.limit (+ id-byteLength id-byteOffset)))]
                          (doto (.slice slice)
                            (.order ByteOrder/LITTLE_ENDIAN)))})
        {:unbind-vao true}]))))

(defn process-image-uri [model-id gltf-dir]
  (map-indexed
   (fn [idx image]
     (let [tex-name (str model-id "_image" idx)
           image    (cond-> image
                      (not (str/starts-with? (:uri image) "data:"))
                      (update :uri (fn [f] (str gltf-dir "/" f))))]
       (assoc image :tex-name tex-name)))))

(defn get-ibm-inv-mats [gltf-data result-bin]
  ;; assuming only one skin
  (when-let [ibm (some-> gltf-data :skins first :inverseBindMatrices)]
    (let [accessors     (some-> gltf-data :accessors)
          buffer-views  (some-> gltf-data :bufferViews)
          accessor      (get accessors ibm)
          buffer-view   (get buffer-views (:bufferView accessor))
          byteLength    (:byteLength buffer-view)
          byteOffset    (:byteOffset buffer-view)
          ^ints ibm-u8s (let [slice (doto (.duplicate result-bin)
                                      (.position byteOffset)
                                      (.limit (+ byteOffset byteLength)))]
                          (doto (.slice slice)
                            (.order ByteOrder/LITTLE_ENDIAN)))
          ibm-f32s      (.asFloatBuffer ibm-u8s)
          inv-bind-mats (into [] (map (fn [i] (utils/f32s->get-mat4 ibm-f32s i))) (range (:count accessor)))]
      inv-bind-mats)))

(defonce debug-data* (atom {}))

(s/fdef process-as-geom-transform
  :ret ::geom/node+transform)
(defn process-as-geom-transform
  "if node have :matrix, decompose it and attach :translation, :rotation, :scale
   if not, it assumed to have either :translation, :rotation, or :scale, 
   then create :matrix out of it"
  [node]
  (let [matrix (some->> (:matrix node) (apply mat/mat))]
    (if matrix
      (let [decom (decompose-Mat4x4 matrix)]
        (assoc node
               :matrix matrix
               :translation (:translation decom)
               :rotation (:rotation decom)
               :scale (:scale decom)))
      (let [trans     (some-> (:translation node) (v/seq->vec3))
            trans-mat (some-> trans translation-mat)
            rot       (when-let [[x y z w] (some-> (:rotation node))]
                        (q/quaternion w x y z))
            rot-mat   (some-> rot (quat->mat4))
            scale     (some-> (:scale node) (v/seq->vec3))
            scale-mat (some-> scale (scaling-mat))
            matrix    (or (cond->> trans-mat
                            rot-mat   (mat/mulm rot-mat)
                            scale-mat (mat/mulm scale-mat))
                          (mat/eye 4))]
        (assoc node :matrix matrix
               :translation (or trans (v/vec3))
               :rotation (or rot q/ONE)
               :scale (or scale (v/vec3 1.0 1.0 1.0)))))))

(s/fdef node-transform-tree
  :ret ::geom/transform-tree)
(defn node-transform-tree [nodes]
  (let [tree  (tree-seq :children
                        (fn [parent-node]
                          (into []
                                (comp
                                 (map (fn [cid] (nth nodes cid)))
                                 (map process-as-geom-transform))
                                (:children parent-node)))
                        (process-as-geom-transform (first nodes)))]
    ;; this somehow already returns an ordered seq, why? is it an optimization in the assimp part? is it the nature of DFS?
    (assert (apply <= (into [] (map :idx) tree)) "assumption broken: order of resulting seq is not the same as order of :idx")
    tree))

(defn gltf-spell
  "magic to pass to gl-magic/cast-spell"
  [gltf-data result-bin {:keys [model-id use-shader]}]
  (let [gltf-dir   (some-> gltf-data :asset :dir)
        _          (assert gltf-dir "no parent dir data in [:asset :dir]")
        mesh       (some-> gltf-data :meshes first) ;; only handle one mesh for now
        materials  (some-> gltf-data :materials)
        textures   (some-> gltf-data :textures)
        accessors  (some-> gltf-data :accessors)
        images     (into []
                         (process-image-uri model-id gltf-dir)
                         (some-> gltf-data :images))
        primitives (eduction
                    (create-vao-names (str model-id "_" (:name mesh)))
                    (match-textures materials textures images)
                    (some-> mesh :primitives))]
    (swap! debug-data* assoc model-id {:gltf-data gltf-data :bin result-bin})
    ;; assume one glb/gltf = one binary for the time being
    (flatten
     [{:buffer-data result-bin :buffer-type GL45/GL_ARRAY_BUFFER}
      (eduction
       (map (fn [{:keys [tex-name] :as image}]
              {:bind-texture tex-name :image image :for-esse model-id}))
       images)

      (eduction (primitive-spell gltf-data result-bin use-shader) primitives)

      {:insert-facts
       ;; assuming one skin for now
       (let [node-id->joint-id (into {} (map-indexed (fn [joint-id node-id] [node-id joint-id])) (some-> gltf-data :skins first :joints))
             primitives        (into []
                                     (comp (map (fn [p] (select-keys p [:indices :tex-name :vao-name])))
                                           (map (fn [p] (update p :indices (fn [i] (get accessors i))))))
                                     primitives)
             nodes             (map-indexed (fn [idx node] (assoc node :idx idx)) (:nodes gltf-data))
             transform-tree    (into [] (node-transform-tree nodes))
             inv-bind-mats     (get-ibm-inv-mats gltf-data result-bin)
             bones             (into []
                                     (comp (map (fn [{node-id :idx :as node}]
                                                  (let [joint-id (get node-id->joint-id node-id)
                                                        ibm      (get inv-bind-mats joint-id)]
                                                    (when joint-id (assoc node
                                                                          :inv-bind-mat ibm
                                                                          ::bones/joint-id joint-id)))))
                                           (filter some?))
                                     transform-tree)]
         [[model-id ::primitives primitives]
          [model-id ::bones/data bones]
          [model-id ::texture/count (count images)]])}])))

(def default
  (merge {::model-rendering/render-type ::gltf-model}
         model-rendering/default-esse))

(declare render-gltf)

(defn init-fn [world _game]
  (-> world
      (o/insert ::gltf-model ::model-rendering/render-fn render-gltf)))

(def rules
  (o/ruleset
   {::load-gltf
    [:what
     [esse-id ::data gltf-data]
     [esse-id ::bins bins]
     [esse-id ::loading/state :success]
     [esse-id ::shader/program-info program-info]
     :then
     (let [gltf-chant   (gltf-spell gltf-data (first bins) {:model-id esse-id :use-shader program-info})
           summons      (gl-magic/cast-spell gltf-chant)
           gl-facts     (::gl-magic/facts summons)
           gl-data      (::gl-magic/data summons)]
       (s-> (reduce o/insert session gl-facts)
            (o/retract esse-id ::data)
            (o/retract esse-id ::bins)
            (o/insert esse-id {::gl-magic/data gl-data})))]

    ::collect-primitives
    [:what
     [esse-id ::gl-magic/data gl-data]
     [esse-id ::primitives primitives]
     :then
     (println esse-id "is loaded!")
     (s-> session
          (o/retract esse-id ::primitives)
          (o/insert esse-id {::gl-magic/data (assoc gl-data ::primitives primitives)
                             ::gl-magic/casted? true}))]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})

(defn render-gltf
  [{::camera/keys [project* view*]}
   {:keys [program-info gl-data tex-data transform pose-tree skinning-ubo] :as match}]
  #_{:clj-kondo/ignore [:inline-def]}
  (def debug-var match)
  (let [vaos  (::gl-magic/vao gl-data)
        prims (::primitives gl-data)]
    (GL45/glUseProgram (:program program-info))
    (cljgl/set-uniform program-info :u_projection project*)
    (cljgl/set-uniform program-info :u_view view*)
    (cljgl/set-uniform program-info :u_model (mat->float-array transform))

    (when (seq pose-tree)
      (let [^floats joint-mats (bones/create-joint-mats-arr pose-tree)]
        (when (> (alength joint-mats) 0)
          (GL45/glBindBuffer GL45/GL_UNIFORM_BUFFER skinning-ubo)
          (GL45/glBufferSubData GL45/GL_UNIFORM_BUFFER 0 joint-mats)
          (GL45/glBindBuffer GL45/GL_UNIFORM_BUFFER 0))))

    (doseq [{:keys [indices vao-name tex-name]} prims]
      (let [vert-count     (:count indices)
            component-type (:componentType indices)
            vao            (get vaos vao-name)
            tex            (get tex-data tex-name)]
        (when vao
          (when-let [{:keys [gl-texture]} tex]
            (GL45/glBindTexture GL45/GL_TEXTURE_2D gl-texture)
            (cljgl/set-uniform program-info :u_mat_diffuse 0))

          (GL45/glBindVertexArray vao)
          (GL45/glDrawElements GL45/GL_TRIANGLES vert-count component-type 0)
          (GL45/glBindVertexArray 0))))))

(comment
  (require '[com.phronemophobic.viscous :as viscous])

  (viscous/inspect debug-var))
