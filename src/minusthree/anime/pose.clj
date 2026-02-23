(ns minusthree.anime.pose
  (:require
   [clojure.spec.alpha :as s]
   [fastmath.core :as m]
   [fastmath.quaternion :as q]
   [fastmath.vector :as v]
   [minusthree.anime.anime :as anime]
   [minusthree.anime.bones :as bones]))

(s/def ::t #(instance? fastmath.vector.Vec3 %))
(s/def ::r #(instance? fastmath.vector.Vec4 %))
(s/def ::pose (s/keys :opt-un [::r ::t]))

(s/def ::out (s/map-of ::bones/name ::pose))
(s/def ::pose-anime (s/keys :req-un [::anime/in ::out]))
(s/def ::pose-animes (s/coll-of ::pose-anime :kind vector?))

(defn v3 ^fastmath.vector.Vec3 [x y z] (v/vec3 x y z))

(defn qu ^fastmath.vector.Vec4 [angle-in-degree x y z]
  (q/quaternion (m/radians angle-in-degree) (v/vec3 x y z)))

(defn pose-anime->bone-anime [pose-animes]
  (reduce
   (fn [anime {:keys [in out]}]
     (reduce-kv
      (fn [anime' bone posing]
        (cond-> anime'
          (:r posing) (update-in [bone :rotation] (fnil conj []) {:in in :out (:r posing)})
          (:t posing) (update-in [bone :translation] (fnil conj []) {:in in :out (:t posing)})))
      anime out))
   {} pose-animes))

(comment
  (let [poseA       {"legL" {:r (qu 15 0 0 0)}
                     "legR" {:r (qu 45 0 0 0)}}
        poseB       {"legL" {:r (qu 45 0 0 0)}
                     "legR" {:r (qu 15 0 0 0)}}
        flailingA   {"handL" {:r (qu 15 0 0 0)
                              :t (v3 10 0 10)}
                     "handR" {:r (qu 15 0 0 0)}}
        flailingB   {"handL" {:r (qu 45 0 0 0)}
                     "handR" {:r (qu 45 0 0 0)}}
        pose-animes [{:in  0.0
                      :out (merge poseA flailingA)}
                     {:in  0.25
                      :out flailingB}
                     {:in  0.5
                      :out (merge poseB flailingA)}
                     {:in  1.0
                      :out flailingB}]]
    (s/assert ::pose-animes pose-animes)
    (s/assert ::anime/bone-anime (pose-anime->bone-anime pose-animes))))

