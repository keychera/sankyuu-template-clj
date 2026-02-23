(ns minusthree.engine.camera 
  (:require
   [fastmath.matrix :refer [mat->float-array]]
   [fastmath.vector :as v]
   [minusthree.engine.math :refer [look-at perspective]]))


;; need hammock to decide where to abstract these project-view stufff
(def project*
  (let [[w h]   [540 540]
        fov     45.0
        aspect  (/ w h)]
    (-> (perspective fov aspect 0.1 1000) mat->float-array)))

(def cam-distance 24.0)

(def view*
  (let [position       (v/vec3 0.0 12.0 cam-distance)
        look-at-target (v/vec3 0.0 12.0 0.0)
        up             (v/vec3 0.0 1.0 0.0)]
    (-> (look-at position look-at-target up) mat->float-array)))

(defn init [game]
  (assoc game
         ::project* project*
         ::view* view*
         ::distance cam-distance))
