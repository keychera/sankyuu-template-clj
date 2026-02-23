(ns minusthree.model.pmx-model
  (:require
   [clojure.spec.alpha :as s]
   [fastmath.matrix :as mat :refer [mat->float-array]]
   [fastmath.quaternion :as q]
   [fastmath.vector :as v]
   [minusthree.anime.bones :as bones]
   [minusthree.engine.geom :as geom]
   [minusthree.engine.macros :refer [s-> vars->map]]
   [minusthree.engine.math :refer [translation-mat]]
   [minusthree.engine.utils :refer [get-parent-path]]
   [minusthree.engine.world :as world]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.gl.shader :as shader]
   [minusthree.gl.texture :as texture]
   [minusthree.model.model-rendering :as model-rendering]
   [minusthree.model.pmx-parser :as pmx-parser]
   [odoyle.rules :as o])
  (:import
   [org.lwjgl.opengl GL45]))

;; PMX model
(s/def ::bones ::geom/transform-tree)
(s/def ::data (s/keys :req-un [::bones]))

(declare load-pmx-model)

(defn load-pmx-fn
  ([esse-id model-path] (load-pmx-fn esse-id model-path {}))
  ([esse-id model-path config]
   (fn []
     (let [pmx-data (load-pmx-model model-path config)]
       [[esse-id ::data pmx-data]]))))

(defn reduce-to-WEIGHTS-and-JOINTS
  "assuming all bones weights are :BDEF1, :BDEF2, or :BDEF4"
  [len]
  (let [WEIGHTS (float-array (* 4 len))
        JOINTS  (int-array (* 4 len))]
    (fn
      ([] 0)
      ([_counter] (vars->map WEIGHTS JOINTS))
      ([counter weight]
       (let [i (* counter 4)
             b1 (or (:bone-index1 weight) 0)
             b2 (or (:bone-index2 weight) 0)
             b3 (or (:bone-index3 weight) 0)
             b4 (or (:bone-index4 weight) 0)]
         (case (:weight-type weight)
           :BDEF1 (aset WEIGHTS i 1.0)
           :BDEF2
           (let [w1 (:weight1 weight) w2 (- 1.0 w1)]
             (doto WEIGHTS (aset i w1) (aset (+ 1 i) w2)))
           :BDEF4
           (let [w1 (:weight1 weight) w2 (:weight2 weight) w3 (:weight3 weight) w4 (:weight4 weight)]
             (doto WEIGHTS (aset i w1) (aset (+ 1 i) w2) (aset (+ 2 i) w3) (aset (+ 3 i) w4)))
           :noop)
         (doto JOINTS (aset i b1) (aset (+ 1 i) b2) (aset (+ 2 i) b3) (aset (+ 3 i) b4)))
       (inc counter)))))

(defn accumulate-face-count [rf]
  (let [acc (volatile! 0)]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input]
       (let [input (assoc input :face-offset @acc)]
         (vswap! acc + (:face-count input))
         (rf result input))))))

(defn pmx-coord->opengl-coord [[x y z]] [x y (- z)])

(defn ccw-face [faces]
  (into [] (comp (partition-all 3) (map (fn [[i0 i1 i2]] [i0 i2 i1])) cat) faces))

(defn resolve-pmx-bones
  "current understanding: pmx bones only have global translation each bones
   this fn will resolve the value of local translation, rotation, invert bind matrix, etc.
   local transform need to be calculated from trans and rot (no scale for now).
   
   assumes bone have :idx denoting its own position in the vector"
  [rf]
  (let [parents-pos! (volatile! {})]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result {:keys [idx parent-bone-idx position] :as bone}]
       (let [position     (pmx-coord->opengl-coord position)
             parent       (get @parents-pos! parent-bone-idx)
             local-pos    (if parent
                            (apply v/vec3 (mapv - position (:position parent)))
                            (v/vec3))
             global-trans (translation-mat position)
             inv-bind-mat (mat/inverse global-trans)
             updated-bone (-> bone
                              (update-keys (fn [k] (or ({:local-name :name} k) k)))
                              (dissoc :position)
                              (assoc ::bones/joint-id idx
                                     :translation      local-pos
                                     :rotation         q/ONE
                                     :scale            (v/vec3 1.0 1.0 1.0)
                                     :inv-bind-mat     inv-bind-mat
                                     :global-transform global-trans))]
         (vswap! parents-pos! assoc idx {:position  position})
         (rf result updated-bone))))))

(defn build-children [pmx-bones]
  (reduce
   (fn [acc-bones [idx bone]]
     (let [parent-idx (int (:parent-bone-idx bone))]
       (if (and parent-idx (not= parent-idx -1))
         (update-in acc-bones [parent-idx :children] (fnil conj []) idx)
         acc-bones)))
   pmx-bones
   (map-indexed vector pmx-bones)))

(defn load-pmx-model
  ([model-path] (load-pmx-model model-path {}))
  ([model-path {:keys [resource-fixer]}]
   (let [res-path  (str "public/" model-path)
         pmx-data  (time (pmx-parser/parse-pmx res-path))
         vertices  (:vertices pmx-data)
         POSITION  (float-array (into [] (comp (map :position) (map pmx-coord->opengl-coord) cat) vertices))
         NORMAL    (float-array (into [] (comp (map :normal) (map pmx-coord->opengl-coord) cat) vertices))
         TEXCOORD  (float-array (into [] (mapcat :uv) vertices))
         vert-wj   (transduce (map :weight)
                              (reduce-to-WEIGHTS-and-JOINTS (count vertices))
                              vertices)
         WEIGHTS   (:WEIGHTS vert-wj)
         JOINTS    (:JOINTS vert-wj)
         INDICES   (int-array (ccw-face (:faces pmx-data)))
         parent    (get-parent-path model-path)
         textures  (into [] (map #(str parent "/" (resource-fixer %))) (:textures pmx-data))
         materials (into [] accumulate-face-count (:materials pmx-data))
         bones     (into []
                         (comp (map-indexed (fn [idx bone] (assoc bone :idx idx)))
                               resolve-pmx-bones)
                         (build-children (:bones pmx-data)))
         morphs    (:morphs pmx-data)]
     (vars->map POSITION NORMAL TEXCOORD WEIGHTS JOINTS INDICES
                textures materials bones morphs))))

(defn pmx-spell [data shader-program {:keys [esse-id]}]
  (let [textures (:textures data)]
    (->> [{:bind-vao esse-id}
          {:buffer-data (:POSITION data) :buffer-type GL45/GL_ARRAY_BUFFER :buffer-name :position}
          {:point-attr :POSITION :use-shader shader-program :count 3 :component-type GL45/GL_FLOAT}
          {:buffer-data (:NORMAL data) :buffer-type GL45/GL_ARRAY_BUFFER}
          {:point-attr :NORMAL :use-shader shader-program :count 3 :component-type GL45/GL_FLOAT}
          {:buffer-data (:TEXCOORD data) :buffer-type GL45/GL_ARRAY_BUFFER}
          {:point-attr :TEXCOORD :use-shader shader-program :count 2 :component-type GL45/GL_FLOAT}

          {:buffer-data (:WEIGHTS data) :buffer-type GL45/GL_ARRAY_BUFFER}
          {:point-attr :WEIGHTS :use-shader shader-program :count 4 :component-type GL45/GL_FLOAT}
          {:buffer-data (:JOINTS data) :buffer-type GL45/GL_ARRAY_BUFFER}
          {:point-attr :JOINTS :use-shader shader-program :count 4 :component-type GL45/GL_UNSIGNED_INT}

          (eduction
           (map-indexed (fn [tex-idx img-uri] {:bind-texture tex-idx :image {:uri img-uri} :for-esse esse-id}))
           textures)

          {:buffer-data (:INDICES data) :buffer-type GL45/GL_ELEMENT_ARRAY_BUFFER}
          {:unbind-vao true}]
         flatten (into []))))

(def default
  (merge {::model-rendering/render-type ::pmx-model}
         model-rendering/default-esse))

(declare render-pmx)

(defn init-fn [world _game]
  (-> world
      (o/insert ::pmx-model ::model-rendering/render-fn render-pmx)))

(def rules
  (o/ruleset
   {::load-pmx
    [:what
     [esse-id ::data pmx-data]
     [esse-id ::shader/program-info program-info]
     :then
     (println "loading pmx model for" esse-id)
     (let [pmx-chant (pmx-spell pmx-data program-info {:esse-id esse-id})
           summons   (gl-magic/cast-spell pmx-chant)
           gl-facts  (::gl-magic/facts summons)
           gl-data   (assoc (::gl-magic/data summons)
                            :materials (:materials pmx-data))
           bones     (:bones pmx-data)
           tex-count (count (:textures pmx-data))]
       (println "pmx load success! for" esse-id)
       (s-> (reduce o/insert session gl-facts)
            (o/retract esse-id ::data)
            (o/insert esse-id {::gl-magic/data gl-data
                               ::gl-magic/casted? true
                               ::bones/data bones
                               ::texture/count tex-count})))]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})

(defn render-material [tex-data program-info material]
  (let [face-count  (:face-count material)
        face-offset (* 4 (:face-offset material))
        tex         (get tex-data (:texture-index material))]
    (when-let [{:keys [gl-texture]} tex]
      (GL45/glBindTexture GL45/GL_TEXTURE_2D gl-texture)
      (cljgl/set-uniform program-info :u_mat_diffuse 0))

    (GL45/glDrawElements GL45/GL_TRIANGLES face-count GL45/GL_UNSIGNED_INT face-offset)))

(defn render-pmx
  [{:keys [project view]}
   {:keys [esse-id program-info gl-data tex-data transform pose-tree skinning-ubo] :as match}]
  #_{:clj-kondo/ignore [:inline-def]}
  (def debug-var match)
  (let [vaos  (::gl-magic/vao gl-data)
        ;; ^floats POSITION   (:POSITION pmx-data) ;; morphs mutate this in a mutable way!
        vao   (get vaos esse-id)]
    (GL45/glUseProgram (:program program-info))
    (cljgl/set-uniform program-info :u_projection project)
    (cljgl/set-uniform program-info :u_view view)
    (cljgl/set-uniform program-info :u_model (mat->float-array transform))

    (when (seq pose-tree)
      (let [^floats joint-mats (bones/create-joint-mats-arr pose-tree)]
        (when (> (alength joint-mats) 0)
          (GL45/glBindBuffer GL45/GL_UNIFORM_BUFFER skinning-ubo)
          (GL45/glBufferSubData GL45/GL_UNIFORM_BUFFER 0 joint-mats)
          (GL45/glBindBuffer GL45/GL_UNIFORM_BUFFER 0))))

    ;; bufferSubData is bottlenecking rn, visualvm checked, todo optimization
    ;; (GL45/glBindBuffer GL45/GL_ARRAY_BUFFER position-buffer)
    ;; (GL45/glBufferSubData GL45/GL_ARRAY_BUFFER 0 POSITION)

    (GL45/glBindVertexArray vao)
    (doseq [material (:materials gl-data)]
      (render-material tex-data program-info material))
    (GL45/glBindVertexArray 0)))

(comment
  (require '[com.phronemophobic.viscous :as viscous])

  (viscous/inspect debug-var)

  (def miku-pmx (load-pmx-model "assets/models/HatsuneMiku/Hatsune Miku.pmx")))
