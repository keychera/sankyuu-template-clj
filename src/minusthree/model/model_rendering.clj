(ns minusthree.model.model-rendering
  (:require
   [clojure.spec.alpha :as s]
   [minusthree.anime.anime :as anime]
   [minusthree.engine.transform3d :as t3d]
   [minusthree.engine.world :as world :refer [esse]]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.gl.shader :as shader]
   [minusthree.gl.texture :as texture]
   [odoyle.rules :as o])
  (:import
   [org.lwjgl.opengl GL45]))

(def MAX_JOINTS 500) ;; this need to be the same as defined in shader files

(s/def ::render-type qualified-keyword?)
(s/def ::render-fn fn?)
(s/def ::ubo some?)

(def default-esse
  (merge {::texture/data {}}
         t3d/default))

(defn create-ubo [size to-index]
  (let [ubo (GL45/glGenBuffers)]
    (GL45/glBindBuffer GL45/GL_UNIFORM_BUFFER ubo)
    (GL45/glBufferData GL45/GL_UNIFORM_BUFFER size GL45/GL_DYNAMIC_DRAW)
    (GL45/glBindBufferBase GL45/GL_UNIFORM_BUFFER to-index ubo)
    (GL45/glBindBuffer GL45/GL_UNIFORM_BUFFER 0)
    ubo))

(defn init-fn [world _game]
  (-> world
      (esse ::skinning-ubo {::ubo (create-ubo (* MAX_JOINTS 16 4) 0)})))

(def rules
  (o/ruleset
   {::render-model-biasa
    [:what
     [esse-id ::render-type render-type]
     [render-type ::render-fn render-fn]
     [esse-id ::shader/program-info program-info]
     [esse-id ::gl-magic/casted? true]
     [esse-id ::gl-magic/data gl-data]
     [esse-id ::texture/data tex-data]
     [esse-id ::texture/count tex-count]
     [esse-id ::anime/pose pose-tree {:then false}]
     ;; need hammock on how to manage ubo
     [::skinning-ubo ::ubo skinning-ubo]
     [esse-id ::t3d/transform transform]
     :when (= tex-count (count tex-data))
     :then (println esse-id "ready to render!")]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules   #'rules})
