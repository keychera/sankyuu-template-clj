(ns minusthree.gl.texture
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.macros :refer [s-> vars->map]]
   [minusthree.engine.utils :as utils]
   [minusthree.engine.world :as world]
   [odoyle.rules :as o])
  (:import
   [org.lwjgl.opengl GL45]
   [org.lwjgl.stb STBImage]))

(s/def ::uri-to-load string?)
(s/def ::for ::world/esse-id)

(s/def ::image-data some?)
(s/def ::width int?)
(s/def ::height int?)
(s/def ::image (s/keys :req-un [::image-data ::width ::height]))

(s/def ::gl-texture some?)
(s/def ::tex-name some?)
(s/def ::texture (s/keys :req-un [::gl-texture]))
(s/def ::data (s/map-of ::tex-name ::texture))
(s/def ::count number?)

(defn load-image [uri]
  (cond
    (or (str/ends-with? uri ".png") (str/ends-with? uri ".bmp"))
    (utils/get-image-from-public-resource uri)

    (str/starts-with? uri "data:image/")
    (utils/get-image-from-data-uri uri)

    :else
    (throw (ex-info (str "uri not supported: " uri) {}))))

(defn cast-texture-spell [data width height]
  (let [texture (GL45/glGenTextures)]
    (GL45/glBindTexture GL45/GL_TEXTURE_2D texture)

    (GL45/glTexImage2D GL45/GL_TEXTURE_2D
                       #_:mip-level    0
                       #_:internal-fmt GL45/GL_RGBA
                       (int width)
                       (int height)
                       #_:border       0
                       #_:src-fmt      GL45/GL_RGBA
                       #_:src-type     GL45/GL_UNSIGNED_BYTE
                       data)
    ;; we free here? https://github.com/LWJGL/lwjgl3/blob/8d12523d40890a78eb11673ce26732a9125971a4/modules/samples/src/test/java/org/lwjgl/demo/stb/Image.java#L222
    ;; above also have an example to generate mipmap TODO
    (STBImage/stbi_image_free data)

    (GL45/glTexParameteri GL45/GL_TEXTURE_2D GL45/GL_TEXTURE_MAG_FILTER GL45/GL_NEAREST)
    (GL45/glTexParameteri GL45/GL_TEXTURE_2D GL45/GL_TEXTURE_MIN_FILTER GL45/GL_NEAREST)
    texture))

(defn create-texture
  ([data width height] (create-texture data width height {}))
  ([data width height
    {:keys [internal-fmt src-fmt]
     :or {internal-fmt GL45/GL_RGBA
          src-fmt GL45/GL_RGBA}}]
   ;; need hammock to reconcile above, complected wth stb_image
   (let [texture (GL45/glGenTextures)]
     (GL45/glBindTexture GL45/GL_TEXTURE_2D texture)
     (GL45/glTexImage2D GL45/GL_TEXTURE_2D
                        #_:mip-level 0
                        internal-fmt
                        (int width)
                        (int height)
                        #_:border    0
                        src-fmt
                        #_:src-type  GL45/GL_UNSIGNED_BYTE
                        data)

     (GL45/glTexParameteri GL45/GL_TEXTURE_2D GL45/GL_TEXTURE_MAG_FILTER GL45/GL_NEAREST)
     (GL45/glTexParameteri GL45/GL_TEXTURE_2D GL45/GL_TEXTURE_MIN_FILTER GL45/GL_NEAREST)
     texture)))

;; below could use a spell like in minusthree.engine.rendering.playground

(defn cast-fbo-spell
  ([width height] (cast-fbo-spell width height {}))
  ([width height {:keys [color-attachment] :as conf
                  :or {color-attachment GL45/GL_COLOR_ATTACHMENT0}}]
   (let [fbo       (GL45/glGenFramebuffers)
         _         (GL45/glBindFramebuffer GL45/GL_FRAMEBUFFER fbo)
         fbo-tex   (GL45/glGenTextures)
         depth-buf (GL45/glGenRenderbuffers)]

     ;; bind, do stuff, unbind, hmm hmm
     ;; attach texture
     (GL45/glBindTexture GL45/GL_TEXTURE_2D fbo-tex)
     (GL45/glTexImage2D GL45/GL_TEXTURE_2D
                        #_:mip-level    0
                        #_:internal-fmt GL45/GL_RGBA
                        (int width)
                        (int height)
                        #_:border       0
                        #_:src-fmt      GL45/GL_RGBA
                        #_:src-type     GL45/GL_UNSIGNED_BYTE
                        0)
     (GL45/glTexParameteri GL45/GL_TEXTURE_2D GL45/GL_TEXTURE_MAG_FILTER GL45/GL_NEAREST)
     (GL45/glTexParameteri GL45/GL_TEXTURE_2D GL45/GL_TEXTURE_MIN_FILTER GL45/GL_NEAREST)
     (GL45/glTexParameteri GL45/GL_TEXTURE_2D GL45/GL_TEXTURE_WRAP_S GL45/GL_CLAMP_TO_EDGE)
     (GL45/glTexParameteri GL45/GL_TEXTURE_2D GL45/GL_TEXTURE_WRAP_T GL45/GL_CLAMP_TO_EDGE)
     (GL45/glBindTexture GL45/GL_TEXTURE_2D 0)
     (GL45/glFramebufferTexture2D GL45/GL_FRAMEBUFFER color-attachment GL45/GL_TEXTURE_2D fbo-tex 0)

     ;; attach depth buffer, will parameterize later or never if not needed 
     (GL45/glBindRenderbuffer GL45/GL_RENDERBUFFER depth-buf);
     (GL45/glRenderbufferStorage GL45/GL_RENDERBUFFER GL45/GL_DEPTH_COMPONENT24 width height);
     (GL45/glFramebufferRenderbuffer GL45/GL_FRAMEBUFFER GL45/GL_DEPTH_ATTACHMENT GL45/GL_RENDERBUFFER depth-buf);

     (when (not= (GL45/glCheckFramebufferStatus GL45/GL_FRAMEBUFFER) GL45/GL_FRAMEBUFFER_COMPLETE)
       (println "warning: framebuffer creation incomplete"))
     (GL45/glBindFramebuffer GL45/GL_FRAMEBUFFER 0)

     (merge conf (vars->map fbo fbo-tex width height)))))

(defn load-texture-fn [tex-name uri]
  (fn []
    (let [image (load-image uri)]
      [[tex-name ::image image]])))

(def rules
  (o/ruleset
   {::load-texture
    [:what
     [tex-name ::uri-to-load uri]
     :then
     (let [uri (str/replace uri #"\\" "/")]
       (s-> session
            (o/retract tex-name ::uri-to-load)
            (o/insert tex-name (loading/push (load-texture-fn tex-name uri)))))]

    ::cast-texture
    [:what
     [tex-name ::image data]
     :then
     (let [{:keys [image-data width height]} data
           texture (cast-texture-spell image-data width height)]
       (s-> session
            (o/retract tex-name ::image)
            (o/insert tex-name ::texture {:gl-texture texture})))]

    ::aggregate
    [:what
     [tex-name ::texture texture]
     [tex-name ::for esse-id]
     [esse-id ::count tex-count]
     [esse-id ::data tex-data {:then false}]
     :then-finally
     ;; still, this part feels expensive
     (let [all-tex-facts   (o/query-all session ::aggregate)
           esse->tex-facts (group-by :esse-id all-tex-facts)]
       (s-> (reduce-kv
             (fn [s' esse-id tex-facts]
               (let [{:keys [tex-count tex-data]} (first tex-facts)
                     texname->texture (-> (group-by :tex-name tex-facts)
                                          (update-vals (comp :texture first)))]
                 (cond-> s'
                   (and (nil? (seq tex-data)) (= (count texname->texture) tex-count))
                   (o/insert esse-id ::data texname->texture))))
             session
             esse->tex-facts)))]}))

(def system
  {::world/rules #'rules})
