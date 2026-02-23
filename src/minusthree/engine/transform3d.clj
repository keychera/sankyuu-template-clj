(ns minusthree.engine.transform3d
  (:require
   [clojure.spec.alpha :as s]
   [minusthree.engine.macros :refer [insert!]]
   [fastmath.matrix :as mat]
   [fastmath.quaternion :as q]
   [fastmath.vector :as v]
   [minusthree.engine.math :refer [quat->mat4 scaling-mat translation-mat]]
   [minusthree.engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::translation #(instance? fastmath.vector.Vec3 %))
(s/def ::rotation #(instance? fastmath.vector.Vec4 %))
(s/def ::scale #(instance? fastmath.vector.Vec3 %))
(s/def ::transform #(instance? fastmath.matrix.Mat4x4 %))

(def default #::{::translation (v/vec3)
                 ::rotation (q/quaternion 0.0)
                 ::scale (v/vec3 1.0 1.0 1.0)})

(def rules
  (o/ruleset
   {::transform
    [:what
     [esse-id ::translation position-vec3]
     [esse-id ::rotation rotation-quat]
     [esse-id ::scale scale-vec3]
     :then
     (let [trans-mat     (translation-mat position-vec3)
           rot-mat       (quat->mat4 rotation-quat)
           scale-mat     (scaling-mat scale-vec3)
           transform-mat (reduce mat/mulm [scale-mat rot-mat trans-mat])]
       (insert! esse-id ::transform transform-mat))]}))

(def system
  {::world/rules #'rules})
