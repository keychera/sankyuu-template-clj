(ns minusthree.model.assimp-lwjgl
  (:require
   [cheshire.core :as json]
   [minusthree.engine.utils :refer [get-parent-path resource->ByteBuffer!?
                                    with-mem-free!?]]
   [minusthree.model.gltf-model :as gltf])
  (:import
   [java.nio ByteOrder]
   [org.lwjgl.assimp AIScene Assimp]))

(defn load-with-assimp
  "load model with assimp and convert to gltf in-memory"
  [model-path ^String export-format]
  (let [flags      (bit-or Assimp/aiProcess_Triangulate
                           Assimp/aiProcess_GenUVCoords
                           Assimp/aiProcess_JoinIdenticalVertices
                           Assimp/aiProcess_SortByPType)
        ^AIScene aiScene
        (with-mem-free!? [buffer!? (resource->ByteBuffer!? (str "public/" model-path))]
          (Assimp/aiImportFileFromMemory buffer!? flags (str nil)))
        _          (assert (some? aiScene) (str "aiScene for " model-path " is null!\nerr: " (Assimp/aiGetErrorString)))
        blob       (Assimp/aiExportSceneToBlob aiScene export-format 0)
        ;; we assume only 2 files for now, or rather, convention for ourself
        gltf-buf   (.data blob)
        gltf-json  (.toString (.decode java.nio.charset.StandardCharsets/UTF_8 gltf-buf))
        gltf       (json/parse-string gltf-json true)
        bin-blob   (.next blob)
        bin-buf    (.data bin-blob)
        _          (.order bin-buf ByteOrder/LITTLE_ENDIAN) ;; actually already little endian, but just to remind me this concept exist (slicing make big endian by default chatgpt says)
        parent-dir (get-parent-path model-path)
        gltf       (assoc-in gltf [:asset :dir] parent-dir)]
    [gltf bin-buf]))

(defn load-gltf-fn [esse-id model-path]
  (fn []
    ;; initially we tried do gl stuff inside load-model-fn but it turns out opengl context only works in one thread
    (let [[gltf bin] (load-with-assimp model-path "gltf2")]
      [[esse-id ::gltf/data gltf]
       [esse-id ::gltf/bins [bin]]])))
