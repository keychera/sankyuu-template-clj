(ns minusthree.stage.sankyuu
  (:require
   [fastmath.core :as m]
   [fastmath.quaternion :as q]
   [fastmath.vector :as v]
   [minusthree.anime.anime :as anime]
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
      (esse ::wolfie gltf-model/default
            (loading/push (load-gltf-fn ::wolfie "models/nondist/SilverWolf/SilverWolf.pmx"))
            {::shader/program-info (cljgl/create-program-info-from-source gltf-vert gltf-frag)
             ::t3d/translation (v/vec3 -5.0 0.0 -5.0)})
      (esse ::miku pmx-model/default
            (loading/push (load-pmx-fn ::miku "models/nondist/HatsuneMiku/Hatsune Miku.pmx"
                                       ;; this is such a weird workaround= in a jar, uri is case-sensitive and this is the only path that is "wrong"
                                       {:resource-fixer (fn [p] (or ({"tex\\face.png" "Tex/face.png"} p) p))}))
            {::shader/program-info (cljgl/create-program-info-from-source pmx-vert pmx-frag)})
      (esse ::wirebeing gltf-model/default
            (loading/push (load-gltf-fn ::wirebeing "models/wirebeing.glb"))
            {::shader/program-info (cljgl/create-program-info-from-source
                                    (raw-from-here "wirecube.vert")
                                    (raw-from-here "wirecube.frag"))})))

(defn post-fn [world _game]
  (-> world
      (esse ::be-cute
            {::anime/duration 1600
             ::anime/bone-animes
             [{"右腕"
               {:rotation
                [{:in 0.0 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 0.5 :out (q/rotation-quaternion (m/radians 30.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 1.0 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}]}}
              ;; we can actually make this in one map, but I am currently hammock-ing about
              ;; how to compose this in a way that time is defined first
              {"左腕"
               {:rotation
                [{:in 0.0 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 0.25 :out (q/rotation-quaternion (m/radians -30.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 0.5 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 0.75 :out (q/rotation-quaternion (m/radians -30.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 1.0 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}]}}]})
      (esse ::wolfie {::anime/use ::be-cute})
      (esse ::miku {::anime/use ::be-cute})
      (esse ::wirebeing {::t3d/translation (v/vec3 -5.0 8.0 0.0)})))

(def system
  {::world/init-fn #'init-fn
   ::world/post-fn #'post-fn})
