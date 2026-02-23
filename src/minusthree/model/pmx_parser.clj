(ns minusthree.model.pmx-parser
  (:require
   [clojure.java.io :as io]
   [gloss.core :as g :refer [defcodec finite-frame repeated string]]
   [gloss.core.codecs :refer [enum header ordered-map]]
   [gloss.core.structure :refer [compile-frame]]
   [gloss.io :as gio]))

;; PMX spec
;; https://gist.github.com/felixjones/f8a06bd48f9da9a4539f/b3944390bd935f48ddf72dd2fc058ffe87c10708
;; https://github.com/hirakuni45/glfw3_app/blob/master/glfw3_app/docs/PMX_spec.txt

;; _f is a convention, indicating a gloss frame
(def empty-frame (compile-frame []))
(def has-flag? (comp not zero? bit-and))
(defn resolve-mask [flags mask]
  (update-vals flags (fn [fs] (has-flag? fs mask))))

(def int_f :int32-le)
(def float_f :float32-le)
(def vec2_f [float_f float_f])
(def vec3_f [float_f float_f float_f])
(def vec4_f [float_f float_f float_f float_f])
;; if index enum is the same keyword as actual types, weird error happens, dont do that
(def vert-index_f (enum :byte {'ubyte 2r001 'uint16-le 2r010 'uint32-le 2r100}))
(def index_f (enum :byte {'byte 2r001 'int16-le 2r010 'uint32-le 2r100}))

(def header-codec
  (ordered-map
   :magic               (string :utf-8 :length 4)
   :version             float_f
   :header-size         :byte
   :encoding            (enum :byte :utf-16LE :utf-8)
   :additional-uv-count :byte
   :vertex-index-size   vert-index_f
   :texture-index-size  index_f
   :material-index-size index_f
   :bone-index-size     index_f
   :morph-index-size    index_f
   :rigid-index-size    index_f))

(declare create-vertex-frame create-material-frame create-bone-frame create-morph-frame)

(defn body-codec-fn
  [{:keys [magic
           encoding
           additional-uv-count
           vertex-index-size
           texture-index-size
           bone-index-size] :as root-header}]
  ;; only check the first three char? 
  ;; https://github.com/MMD-Blender/blender_mmd_tools/blob/b5fe56193f375a305627e8ab646bdf192895d214/mmd_tools/core/pmx/__init__.py#L265C24-L265C41
  (when (not= (subs magic 0 3) "PMX") (throw (ex-info (str "no PMX magic detected! magic = '" magic "'") {})))
  (let [text_f        (finite-frame int_f (string encoding))
        vertex-idx_f  (keyword vertex-index-size)
        bone-idx_f    (keyword bone-index-size)
        texture-idx_f (keyword texture-index-size)]
    (ordered-map
     :root-header    root-header
     :local-name     text_f
     :global-name    text_f
     :local-comment  text_f
     :global-comment text_f
     :vertices       (repeated (create-vertex-frame additional-uv-count bone-idx_f) :prefix int_f)
     :faces          (repeated vertex-idx_f :prefix int_f)
     :textures       (repeated text_f :prefix int_f)
     :materials      (repeated (create-material-frame text_f texture-idx_f) :prefix int_f)
     :bones          (repeated (create-bone-frame text_f bone-idx_f) :prefix int_f)
     :morphs         (repeated (create-morph-frame text_f root-header) :prefix int_f))))

;; vertices

(def weight-type (enum :byte :BDEF1 :BDEF2 :BDEF4 :SDEF #_:QDEF))

(defn weight-type-fn [bone-idx_f weight-type]
  (apply ordered-map
         (concat
          [:weight-type weight-type]
          (case weight-type
            :BDEF1 [:bone-index1 bone-idx_f]
            :BDEF2 [:bone-index1 bone-idx_f
                    :bone-index2 bone-idx_f
                    :weight1 float_f]
            :BDEF4 [:bone-index1 bone-idx_f
                    :bone-index2 bone-idx_f
                    :bone-index3 bone-idx_f
                    :bone-index4 bone-idx_f
                    :weight1 float_f
                    :weight2 float_f
                    :weight3 float_f
                    :weight4 float_f]
            :SDEF  [:bone-index1 bone-idx_f
                    :bone-index2 bone-idx_f
                    :weight1 float_f
                    :C       vec3_f
                    :R0      vec3_f
                    :R1      vec3_f]))))

(def weight-type-memo (memoize weight-type-fn))

(defn weight-body-fn [bone-idx_f]
  (fn [weight-type] (weight-type-memo bone-idx_f weight-type)))

(def weight-body-memo (memoize weight-body-fn))

(defn create-weight-codec [bone-idx_f]
  (header weight-type (weight-body-memo bone-idx_f) identity))

(defn create-vertex-frame [additional-uv-count bone-idx_f]
  (let [weight-codec  (create-weight-codec bone-idx_f)]
    (ordered-map
     :position      vec3_f
     :normal        vec3_f
     :uv            vec2_f
     :additional-uv (into [] (take additional-uv-count (repeat vec4_f)))
     :weight        weight-codec
     :edge-scale    float_f)))

;; materials

(defn toon-fn [texture-idx_f toon-flag]
  (apply ordered-map
         [:toon-flag  toon-flag
          :toon-index (case toon-flag
                        :texture texture-idx_f
                        :inbuilt :byte)]))

(def toon-memo (memoize toon-fn))

(defn toon-body-fn [texture-idx_f]
  (fn [toon-flag] (toon-memo texture-idx_f toon-flag)))

(def toon-body-memo (memoize toon-body-fn))

(defn create-toon-codec [texture-idx_f]
  (header (enum :byte :texture :inbuilt) (toon-body-memo texture-idx_f) identity))

(def drawing-mode-flag
  {:no-cull        0x01
   :ground-shadow  0x02
   :draw-shadow    0x04
   :receive-shadow 0x08
   :has-edge       0x10})

(defn create-material-frame [text_f texture-idx_f]
  (let [toon-codec (create-toon-codec texture-idx_f)]
    (compile-frame
     (ordered-map
      :local-name        text_f
      :global-name       text_f
      :diffuse-color     vec4_f
      :specular-color    vec3_f
      :specularity       float_f
      :ambient-color     vec3_f
      :drawing-mode      :byte
      :edge-color        vec4_f
      :edge-size         float_f
      :texture-index     texture-idx_f
      :env-texture-index texture-idx_f
      :env-mode          :byte
      :toon              toon-codec
      :memo              text_f
      :face-count        int_f)
     identity
     (fn [res] (update res :drawing-mode (fn [mask] (resolve-mask drawing-mode-flag mask)))))))

;; bones

;; this one is better regarding the bone flags part
;; https://gist.github.com/hakanai/d442724ac3728c1b50e50f7d1df65e1b#file-pmx21-md
(def bone-flags
  {:connection           0x0001
   :rotatable?           0x0002
   :translatable?        0x0004
   :visible?             0x0008
   :enabled?             0x0010
   :IK                   0x0020
   :parent-local?        0x0040 ;; https://gist.github.com/felixjones/f8a06bd48f9da9a4539f?permalink_comment_id=4559705#gistcomment-4559705
   :inherit-scale        0x0080 ;; our interpretation
   :inherit-rotation     0x0100
   :inherit-translation  0x0200
   :fixed-axis           0x0400
   :local-axis           0x0800
   :after-physics-deform 0x1000})

(defn create-angle-limits-frame []
  (ordered-map :lower vec3_f :upper vec3_f))

(defn limit-angle-fn [limit-angle?]
  (if limit-angle?
    (create-angle-limits-frame)
    empty-frame))

(def limit-angle-memo (memoize limit-angle-fn))

(defn create-IK-link-frame [bone-idx_f]
  (ordered-map
   :IK-bone-idx  bone-idx_f
   :angle-limits (header (enum :byte false true) limit-angle-memo identity)))

(defn bone-mask-fn [bone-idx_f bone-mask]
  (let [flags (resolve-mask bone-flags bone-mask)]
    ;; debugging purposes, I wonder if gloss' debuggability can be improved
    ;; seems like this is the way to go! https://github.com/H31MDALLR/redis-clojure/blob/0de431c284d55253f29723b86e128813523b2a34/src/redis/rdb/schema.clj#L533C6-L533C53
    #_(println flags)
    (apply ordered-map
           (cond-> [:rotatable?    (:rotatable? flags)
                    :translatable? (:translatable? flags)
                    :visible?      (:visible? flags)
                    :enabled?      (:enabled? flags)]
             ;; the order is clearer here https://github.com/hirakuni45/glfw3_app/blob/master/glfw3_app/docs/PMX_spec.txt
             ;; 接続先:0 の場合
             (:connection flags)
             (conj :connection bone-idx_f)

             ;; 接続先:1 の場合
             (not (:connection flags))
             (conj :position-offset vec3_f)

             ;; 回転付与:1 または 移動付与:1 の場合
             (or (:inherit-rotation flags)
                 (:inherit-translation flags))
             (conj :parent-idx       bone-idx_f
                   :parent-influence float_f)

             ;; 軸固定:1 の場合
             (:fixed-axis flags)
             (conj :axis-vector vec3_f)

             ;; ローカル軸:1 の場合
             (:local-axis flags)
             (conj :x-axis-vector vec3_f
                   :z-axis-vector vec3_f)

             ;; 外部親変形:1 の場合
             (:external-parent-deform flags)
             (conj :key-value int_f)

             (:IK flags)
             (conj :IK-bone-idx bone-idx_f
                   :iterations  int_f
                   :limit-angle float_f
                   :links       (repeated (create-IK-link-frame bone-idx_f) :prefix int_f))))))

(def bone-mask-memo (memoize bone-mask-fn))

(defn bone-body-fn [bone-idx_f]
  (fn bone-mask-fn [bone-mask]
    (bone-mask-memo bone-idx_f bone-mask)))

(def bone-body-memo (memoize bone-body-fn))

(defn create-bone-codec [bone-idx_f]
  (header (compile-frame :int16-le) (bone-body-memo bone-idx_f) identity))

(defn create-bone-frame [text_f bone-idx_f]
  (let [bone-codec (create-bone-codec bone-idx_f)]
    (ordered-map
     :local-name      text_f
     :global-name     text_f
     :position        vec3_f
     :parent-bone-idx bone-idx_f
     :transform-level int_f
     :bone-data       bone-codec)))

;; morph

(defcodec morph-type-frame
  (ordered-map
   :panel-type :byte
   :morph-type (enum :byte :group :vertex :bone :uv :uv-ext1 :uv-ext2 :uv-ext3 :uv-ext4 :material #_:flip #_:impulse)))

(defn group-offset [morph-idx_f]
  (compile-frame
   (ordered-map
    :morph-idx morph-idx_f
    :influence float_f)))

(defn vertex-offset [vertex-idx_f]
  (compile-frame
   (ordered-map
    :vertex-idx  vertex-idx_f
    :translation vec3_f)))

(defn bone-offset [bone-idx_f]
  (compile-frame
   (ordered-map
    :bone-idx    bone-idx_f
    :translation vec3_f
    :rotation    vec4_f)))

(defn uv-offset [vertex-idx_f]
  (compile-frame
   (ordered-map
    :vertex-idx vertex-idx_f
    :floats     vec4_f)))

(defn material-offset [material-idx_f]
  (compile-frame
   (ordered-map
    :material-idx material-idx_f
    :hmm?             :byte
    :diffuse-color    vec4_f
    :specular-color   vec3_f
    :specularity      float_f
    :ambient-color    vec3_f
    :edge-color       vec4_f
    :edge-size        float_f
    :texture-tint     vec4_f
    :environment-tint vec4_f
    :toon-tint        vec4_f)))

(defn create-offset-codec [morph-type morph-idx_f vertex-idx_f bone-idx_f material-idx_f]
  (case morph-type
    :group    (group-offset morph-idx_f)
    :vertex   (vertex-offset vertex-idx_f)
    :bone     (bone-offset bone-idx_f)
    :uv       (uv-offset vertex-idx_f)
    :uv-ext1  (uv-offset vertex-idx_f)
    :uv-ext2  (uv-offset vertex-idx_f)
    :uv-ext3  (uv-offset vertex-idx_f)
    :uv-ext4  (uv-offset vertex-idx_f)
    :material (material-offset material-idx_f)))

(defn morph-type-fn [{:keys [morph-type] :as types} morph-idx_f vertex-idx_f bone-idx_f material-idx_f]
  (ordered-map
   :types       types
   :offset-data (repeated (create-offset-codec morph-type morph-idx_f vertex-idx_f bone-idx_f material-idx_f) :prefix int_f)))

(def morph-type-memo (memoize morph-type-fn))

(defn morph-body-fn [morph-idx_f vertex-idx_f bone-idx_f material-idx_f]
  (fn morph-type-fn [types] (morph-type-memo types morph-idx_f vertex-idx_f bone-idx_f material-idx_f)))

(def morph-body-memo (memoize morph-body-fn))

(defn create-morph-frame
  [text_f
   {:keys [vertex-index-size
           bone-index-size
           morph-index-size
           material-index-size] :as _root-header}]
  (let [morph-idx_f    (keyword morph-index-size)
        vertex-idx_f   (keyword vertex-index-size)
        bone-idx_f     (keyword bone-index-size)
        material-idx_f (keyword material-index-size)
        morph-offset-codec
        (header morph-type-frame (morph-body-memo morph-idx_f vertex-idx_f bone-idx_f material-idx_f) identity)]
    (ordered-map
     :local-name  text_f
     :global-name text_f
     :offsets     morph-offset-codec)))

(def pmx-codec (header header-codec body-codec-fn identity))

(defn parse-pmx [pmx-path]
  (println "parsing pmx:" pmx-path)
  (with-open [is (io/input-stream (io/resource pmx-path))]
    (let [bytes (.readAllBytes is)
          buf   (java.nio.ByteBuffer/wrap bytes)]
      (gio/decode pmx-codec buf false))))

(comment
  #_(require '[clj-async-profiler.core :as prof])
  (identity #_prof/profile
   (time
    (let [model-path
          #_"public/assets/models/Alicia_blade.pmx"
          "public/assets/models/SilverWolf/SilverWolf.pmx"
          result     (parse-pmx model-path)]
      (def hmm result)
      (-> result
          (update :vertices (juxt count #(into [] (take 2) %)))
          (update :faces (juxt count identity))
          (update :materials (juxt count #(into [] (take 2) %)))
          (update :bones (juxt count #(into [] (comp (take 2)) %)))
          (update :morphs (juxt count #(into [] (comp (map (juxt :local-name (comp count :offset-data :offsets)))) %)))))))

  (let [verts (float-array (into [] (:faces hmm)))]
    (float-array verts))

  (alength *1)
  *e

  :-)

;; other refs;
;; https://gist.github.com/ulrikdamm/8274171
