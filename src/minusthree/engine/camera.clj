(ns minusthree.engine.camera
  (:require
   [clojure.spec.alpha :as s]
   [fastmath.matrix :refer [mat->float-array]]
   [fastmath.vector :as v]
   [minusthree.engine.math :refer [look-at perspective]]
   [minusthree.engine.types :as types]
   [minusthree.engine.world :as world]
   [odoyle.rules :as o]))


;; need hammock to decide where to abstract these project-view stufff
(def project-arr
  (let [[w h]   [540 540]
        fov     45.0
        aspect  (/ w h)]
    (-> (perspective fov aspect 0.1 1000) mat->float-array)))

(def initial-distance 8.0)

(def view-arr
  (let [position       (v/vec3 0.0 18.0 initial-distance)
        look-at-target (v/vec3 0.0 18.0 0.0)
        up             (v/vec3 0.0 1.0 0.0)]
    (-> (look-at position look-at-target up) mat->float-array)))

(s/def ::project* ::types/f32-arr)
(s/def ::view* ::types/f32-arr)
(s/def ::distance float?)

(defn init-fn [world _game]
  (-> world
      (o/insert ::global {::project* project-arr ::view* view-arr ::distance initial-distance})))

(def rules
  (o/ruleset
   {::global-camera
    [:what
     [::global ::project* project*]
     [::global ::view* view*]
     [::global ::distance distance]]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})

(defn get-active-cam [game]
  (first (o/query-all (::world/this game) ::global-camera)))
