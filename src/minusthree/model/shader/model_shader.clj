(ns minusthree.model.shader.model-shader 
  (:require
   [minusthree.engine.utils :refer [raw-from-here]]))

(def gltf-vert (raw-from-here "gltf_model.vert"))
(def gltf-frag (raw-from-here "gltf_model.frag"))
(def pmx-vert (raw-from-here  "pmx_model.vert"))
(def pmx-frag (raw-from-here  "pmx_model.frag"))
