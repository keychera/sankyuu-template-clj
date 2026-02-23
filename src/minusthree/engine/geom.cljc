(ns minusthree.engine.geom
  (:require
   [clojure.spec.alpha :as s]
   [minusthree.engine.transform3d :as t3d]
   [fastmath.matrix]))

(s/def ::matrix #(instance? fastmath.matrix.Mat4x4 %))
(s/def ::node+transform (s/keys :req-un [::t3d/translation ::t3d/rotation] :opt-un [::matrix ::t3d/scale]))
(s/def ::transform-tree (s/coll-of ::node+transform :kind vector?))
