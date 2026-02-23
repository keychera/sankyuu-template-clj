(ns minusthree.engine.rendering
  (:require
   [fastmath.matrix :refer [mat->float-array]]
   [fastmath.vector :as v]
   [minusthree.engine.math :refer [look-at perspective]]
   [minusthree.engine.world :as world]
   [minusthree.model.model-rendering :as model-rendering]
   [odoyle.rules :as o])
  (:import
   [org.lwjgl.opengl GL45]))

;; need hammock to decide where to abstract these project-view stufff
(def project
  (let [[w h]   [540 540]
        fov     45.0
        aspect  (/ w h)]
    (-> (perspective fov aspect 0.1 1000) mat->float-array)))

(def cam-distance 24.0)

(def view
  (let [position       (v/vec3 0.0 12.0 cam-distance)
        look-at-target (v/vec3 0.0 12.0 0.0)
        up             (v/vec3 0.0 1.0 0.0)]
    (-> (look-at position look-at-target up) mat->float-array)))

(defn init [game]
  (GL45/glEnable GL45/GL_BLEND)
  (GL45/glEnable GL45/GL_BLEND)
  (GL45/glEnable GL45/GL_CULL_FACE)
  (GL45/glEnable GL45/GL_MULTISAMPLE)
  (GL45/glEnable GL45/GL_DEPTH_TEST)
  (assoc game
         :project project
         :view view))

(defn rendering-zone [game]
  (let [{:keys [config]} game
        {:keys [w h]}    (:window-conf config)]
    (GL45/glBlendFunc GL45/GL_SRC_ALPHA GL45/GL_ONE_MINUS_SRC_ALPHA)
    (GL45/glClearColor (/ 0x38 0xff) (/ 0x32 0xff) (/ 0x2c 0xff) 1.0)
    (GL45/glClear (bit-or GL45/GL_COLOR_BUFFER_BIT GL45/GL_DEPTH_BUFFER_BIT))
    (GL45/glViewport 0 0 w h)

    (let [world   (::world/this game)
          renders (o/query-all world ::model-rendering/render-model-biasa)]
      (doseq [{:keys [render-fn]
               :as   match} renders]
        (render-fn game match))))
  game)

(defn destroy [game]
  game)
