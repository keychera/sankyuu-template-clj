(ns minusthree.engine.camera
  (:require
   [clojure.spec.alpha :as s]
   [fastmath.matrix :as mat :refer [mat->float-array]]
   [fastmath.vector :as v]
   [minusthree.engine.macros :refer [s->]]
   [minusthree.engine.math :refer [decompose-Mat4x4 look-at perspective]]
   [minusthree.engine.types :as types]
   [minusthree.engine.world :as world]
   [odoyle.rules :as o])
  (:import
   [fastmath.vector Vec3]))


;; need hammock to decide where to abstract these project-view stufff
(def project-arr
  (let [[w h]   [540 540]
        fov     45.0
        aspect  (/ w h)]
    (-> (perspective fov aspect 0.1 1000) mat->float-array)))

(def initial-distance 8.0)
(def initial-target (v/vec3 0.0 18.0 0.0))
(def up (v/vec3 0.0 1.0 0.0))

(defn make-view-arr [target position]
  (let [up (v/vec3 0.0 1.0 0.0)]
    (-> (look-at position target up) mat->float-array)))

(s/def ::project* ::types/f32-arr)
(s/def ::view* ::types/f32-arr)
(s/def ::target #(instance? Vec3 %))
(s/def ::distance float?)

(defn init-fn [world _game]
  (-> world
      (o/insert ::global {::project* project-arr
                          ::view* (float-array [])
                          ::target initial-target
                          ::distance initial-distance})))

(def rules
  (o/ruleset
   {::global-camera
    [:what
     [::global ::project* project* {:then false}]
     [::global ::view* view* {:then false}]
     [::global ::target target {:then false}]
     [::global ::distance distance {:then false}]]

    ::update-distance
    [:what
     [::global ::view* view* {:then false}]
     [::global ::target target {:then false}]
     [::global ::distance distance]
     :when (> distance 1.0)
     :then
     (let [decom    (when (> (alength view*) 0) (decompose-Mat4x4 (mat/inverse (apply mat/mat (vec view*)))))
           position (or (:translation decom) (v/add initial-target (v/vec3 0.0 0.0 initial-distance)))
           diff     (v/sub position target)
           mag-diff (v/mag diff)
           factor   (/ (- distance mag-diff) mag-diff)
           new-diff (v/mult diff factor)
           new-pos  (v/add position new-diff)
           view     (-> (look-at new-pos target up) mat->float-array)]
       (s-> session (o/insert ::global {::view* view})))]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})

(defn get-active-cam [game]
  (first (o/query-all (::world/this game) ::global-camera)))
