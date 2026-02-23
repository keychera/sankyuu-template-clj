(ns minusthree.anime.bones
  (:require
   [clojure.spec.alpha :as s]
   [fastmath.matrix :as mat]
   [minusthree.engine.math :refer [quat->mat4 scaling-mat translation-mat]]))

(s/def ::name string?)
(s/def ::data vector?)

(defn calc-local-transform [{:keys [translation rotation scale]}]
  (let [trans-mat    (translation-mat translation)
        rot-mat      (quat->mat4 rotation)
        scale-mat    (scaling-mat scale)
        local-trans  (reduce mat/mulm [scale-mat rot-mat trans-mat])]
    local-trans))

;; transducer with assumption that parent node will always before child node in a linear seq
(defn global-transform-xf [rf]
  (let [parents-global-transform! (volatile! {})]
    (fn
      ([] (rf))
      ([result]
       (rf result))
      ([result node]
       (let [local-trans  (calc-local-transform node)
             parent-trans (get @parents-global-transform! (:idx node))
             global-trans (if parent-trans
                            (mat/mulm local-trans parent-trans)
                            local-trans)
             node         (assoc node :global-transform global-trans)]
         (when (:children node)
           (vswap! parents-global-transform!
                   into (map (fn [cid] [cid global-trans]))
                   (:children node)))
         (rf result node))))))

(defn create-joint-mats-arr [bones]
  (let [f32s (float-array (* 16 (count bones)))]
    (doseq [{::keys [joint-id]
             :keys [inv-bind-mat global-transform]} bones]
      (let [joint-mat (mat/mulm inv-bind-mat global-transform)
            i         (* joint-id 16)]
        (dotimes [j 16]
          (aset f32s (+ i j) (float (get (mat/row joint-mat (quot j 4)) (mod j 4)))))))
    f32s))
