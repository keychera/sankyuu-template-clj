(ns minusthree.model.model3d 
  (:require
   [minusthree.anime.anime :as anime]
   [minusthree.model.gltf-model :as gltf-model]
   [minusthree.model.model-rendering :as model-rendering]
   [minusthree.model.pmx-model :as pmx-model]))

(def all-system
  [model-rendering/system
   gltf-model/system
   pmx-model/system
   anime/system])
