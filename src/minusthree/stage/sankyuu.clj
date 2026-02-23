(ns minusthree.stage.sankyuu
  (:require
   [fastmath.core :as m]
   [fastmath.quaternion :as q]
   [fastmath.vector :as v]
   [minusthree.anime.anime :as anime]
   [minusthree.anime.pose :refer [pose-anime->bone-anime qu v3]]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.transform3d :as t3d]
   [minusthree.engine.utils :refer [raw-from-here]]
   [minusthree.engine.world :as world :refer [esse]]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.shader :as shader]
   [minusthree.model.assimp-lwjgl :refer [load-gltf-fn]]
   [minusthree.model.gltf-model :as gltf-model]
   [minusthree.model.pmx-model :as pmx-model :refer [load-pmx-fn]]
   [minusthree.model.shader.model-shader :refer [gltf-frag gltf-vert pmx-frag
                                                 pmx-vert]]))

(defn init-fn [world _game]
  (-> world
      (esse ::miku pmx-model/default
            (loading/push (load-pmx-fn ::miku "models/nondist/HatsuneMiku/Hatsune Miku.pmx"
                                       ;; this is such a weird workaround= in a jar, uri is case-sensitive and this is the only path that is "wrong"
                                       {:resource-fixer (fn [p] (or ({"tex\\face.png" "Tex/face.png"} p) p))}))
            {::shader/program-info (cljgl/create-program-info-from-source pmx-vert pmx-frag)})
      (esse ::wirebeing gltf-model/default
            (loading/push (load-gltf-fn ::wirebeing "models/wirebeing.glb"))
            {::shader/program-info (cljgl/create-program-info-from-source
                                    (raw-from-here "wirecube.vert")
                                    (raw-from-here "wirecube.frag"))})
      (esse ::cesium-man gltf-model/default
            (loading/push (load-gltf-fn ::cesium-man "models/nondist/CesiumMan.glb"))
            {::shader/program-info (cljgl/create-program-info-from-source gltf-vert gltf-frag)
             ::t3d/scale (v/vec3 10.0 10.0 10.0)})
      (esse ::clojuremesh gltf-model/default
            (loading/push (load-gltf-fn ::clojuremesh "models/nondist/clj.glb"))
            {::shader/program-info (cljgl/create-program-info-from-source gltf-vert gltf-frag)})))

(def pose-anime
  (let [rest-pose {"右腕"   {:r (qu 90.0 0.5 1.0 0.4)}
                   "右腕捩" {:r (qu -15.0 -0.5 -0.5 0.0)}
                   "右手捩" {:r (qu 180.0 -0.5 -0.5 0.0)}
                   "右ひじ" {:r (qu 10.0 0.0 0.0 -1.0)}

                   "左腕"   {:r (qu 90.0 0.5 -1.0 -0.4)}
                   "左腕捩" {:r (qu 15.0 0.5 -0.5 0.0)}
                   "左手捩" {:r (qu 180.0 0.5 -0.5 0.0)}
                   "左ひじ" {:r (qu 10.0 0.0 0.0 1.0)}}

        poseA     {"右腕"   {:r (qu 90.0 0.0 1.0 0.0)}
                   "右腕捩" {:r (qu -15.0 -0.5 -0.5 0.0)}
                   "右手捩" {:r (qu 180.0 -0.5 -0.5 0.0)}
                   "右ひじ" {:r (qu 70.0 0.0 0.0 -1.0)}

                   "左腕"   {:r (qu 90.0 0.0 -1.0 0.0)}
                   "左腕捩" {:r (qu 15.0 0.5 -0.5 0.0)}
                   "左手捩" {:r (qu 180.0 0.5 -0.5 0.0)}
                   "左ひじ" {:r (qu 70.0 0.0 0.0 1.0)}}

        look-down {"右目" {:r (qu 10.0 1.0 0.0 0.0)}
                   "左目" {:r (qu 10.0 1.0 0.0 0.0)}}
        look-up   {"右目" {:r (qu 0.0 1.0 0.0 0.0)}
                   "左目" {:r (qu 0.0 1.0 0.0 0.0)}}]
    (pose-anime->bone-anime
     [{:in  0.0 :out rest-pose}

      {:in  0.0 :out look-down}
      {:in  0.22 :out look-down}
      {:in  0.25 :out look-up}
      {:in  0.45 :out look-up}

      {:in  0.42 :out poseA}
      {:in  0.96 :out poseA}
      {:in  1.0 :out rest-pose}])))

(def be-clojure
  (let [from-below {"top0" {:t (v3 0.0 -5.0 0.0)}
                    "bottom0" {:t (v3 0.0 -5.0 0.0)}}
        to-middle {"top0" {:t (v3 0.0 -0.7 0.0)}
                   "bottom0" {:t (v3 0.0 -0.7 0.0)}}
        wiggle1 {"top1" {:r (qu 25.0 0.0 1.0 0.0)}
                 "bottom1" {:r (qu 25.0 0.0 1.0 0.0)}}
        wiggle2 {"top1" {:r (qu -25.0 0.0 1.0 0.0)}
                 "bottom1" {:r (qu -25.0 0.0 1.0 0.0)}}]
    (pose-anime->bone-anime
     [{:in 0.0 :out from-below}
      {:in 0.42 :out to-middle}
      {:in 0.96 :out to-middle}
      {:in 1.0 :out from-below}
      
      {:in 0.0 :out wiggle1}
      {:in 0.25 :out wiggle2}
      {:in 0.5 :out wiggle1}
      {:in 0.75 :out wiggle2}
      {:in 1.0 :out wiggle1}])))

(defn post-fn [world _game]
  (-> world
      (esse ::clojuremesh
            {::t3d/rotation (qu 90 0.0 1.0 0.0)
             ::t3d/translation (v3 0.0 18.0 5.0)
             ::t3d/scale (v3 0.5 0.5 0.5)
             ::anime/use ::be-clojure})
      (esse ::cesium-man
            {::t3d/translation (v/vec3 5.0 0.0 5.0)
             ::t3d/rotation (q/mult (q/rotation-quaternion (m/radians -90) (v/vec3 1.0 0.0 0.0)) (q/rotation-quaternion (m/radians -90) (v/vec3 0.0 0.0 1.0)))})
      (esse ::be-clojure
            {::anime/duration 3200
             ::anime/bone-animes [be-clojure]})
      (esse ::be-awesome
            {::anime/duration 3200
             ::anime/bone-animes [pose-anime]})
      (esse ::miku {::anime/use ::be-awesome})
      (esse ::wirebeing {::t3d/translation (v/vec3 -5.0 14.0 0.0)})))

(def system
  {::world/init-fn #'init-fn
   ::world/post-fn #'post-fn})
