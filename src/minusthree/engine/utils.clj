(ns minusthree.engine.utils
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [fastmath.matrix :as mat])
  (:import
   [java.nio ByteBuffer FloatBuffer]
   [java.nio.channels Channels ReadableByteChannel]
   [java.util Base64]
   [org.lwjgl.stb STBImage]
   [org.lwjgl.system MemoryStack MemoryUtil]))

(defn ^:vibe get-parent-path [^String path-str]
  (let [last-slash (max (.lastIndexOf path-str "/")
                        (.lastIndexOf path-str "\\"))]
    (if (pos? last-slash)
      (subs path-str 0 last-slash)
      "")))

(defn resize-buffer ^ByteBuffer [^ByteBuffer old-buf ^long new-capacity]
  (MemoryUtil/memRealloc old-buf new-capacity))

(defmacro ^:vibe with-mem-free!?
  "macro like with-open but memFree instead of close. suffix !? is convention denoting must free"
  [bindings & body]
  (let [syms (take-nth 2 bindings)
        forms (take-nth 2 (rest bindings))]
    `(let [~@(interleave syms forms)]
       (try ~@body (finally ~@(map (fn [s] `(org.lwjgl.system.MemoryUtil/memFree ~s)) syms))))))

;; https://github.com/LWJGL/lwjgl3/blob/master/modules/samples/src/test/java/org/lwjgl/demo/util/IOUtil.java#L40
(defn resource->ByteBuffer!?
  "create a DirectByteBuffer from resource. We begin to C the truth"
  (^ByteBuffer [resource-path] (resource->ByteBuffer!? resource-path (* 8 1024)))
  (^ByteBuffer [resource-path initial-buf-size]
   (with-open [is  (io/input-stream (io/resource resource-path))
               rbc ^ReadableByteChannel (Channels/newChannel is)]
     (let [^ByteBuffer buf!?
           (loop [buffer (MemoryUtil/memAlloc initial-buf-size)]
             (if (== (.read rbc buffer) -1)
               buffer
               (recur
                (if (== (.remaining buffer) 0)
                  (resize-buffer buffer (int (* (.capacity buffer) 1.5)))
                  buffer))))]
       (.flip buf!?)
       (MemoryUtil/memSlice buf!?)))))

(defn base64-uri->ByteBuffer!?
  "Decodes a data URI directly into a DirectByteBuffer"
  ^ByteBuffer [uri]
  (let [base64-str (second (clojure.string/split uri #","))
        bytes      (.decode (Base64/getDecoder) ^String base64-str)
        buffer     (MemoryUtil/memAlloc (count bytes))]
    (.put buffer bytes)
    (.flip buffer)
    buffer))

(defn stb-load-from-buffer [buffer!?]
  (with-open [stack (MemoryStack/stackPush)]
    (let [*w    (.mallocInt stack 1)
          *h    (.mallocInt stack 1)
          *comp (.mallocInt stack 1)
          image (STBImage/stbi_load_from_memory buffer!? *w *h *comp STBImage/STBI_rgb_alpha)]
      (when (nil? image)
        (throw (ex-info (str "get-image failed! reason: " (STBImage/stbi_failure_reason)) {})))
      {:image-data image :width (.get *w) :height (.get *h)})))

(defn get-image-from-public-resource [public-resource-path]
  (with-mem-free!? [buffer!? (resource->ByteBuffer!? (str "public/" public-resource-path))]
    (stb-load-from-buffer buffer!?)))

(defn get-image-from-data-uri [data-uri]
  (with-mem-free!? [buffer!? (base64-uri->ByteBuffer!? data-uri)]
    (stb-load-from-buffer buffer!?)))

(defn f32s->get-mat4
  "Return a 4x4 matrix from a float-array / Float32Array `f32s`.
  `idx` is the start index (optional, defaults to 0)."
  ([^FloatBuffer fb idx]
   (let [^long i (* (or idx 0) 16)]
     (mat/mat
      (.get fb i)       (.get fb (+ i 1))  (.get fb (+ i 2))  (.get fb (+ i 3))
      (.get fb (+ i 4)) (.get fb (+ i 5))  (.get fb (+ i 6))  (.get fb (+ i 7))
      (.get fb (+ i 8)) (.get fb (+ i 9))  (.get fb (+ i 10)) (.get fb (+ i 11))
      (.get fb (+ i 12)) (.get fb (+ i 13)) (.get fb (+ i 14)) (.get fb (+ i 15))))))

(defmacro raw-from
  "raw source slurped at compile time"
  [& paths]
  (slurp (io/resource (str/join "/" paths))))

(defmacro raw-from-here
  "raw source slurped at compile time"
  [& paths]
  (let [ns-segment (-> (str *ns*) (str/split #"\.") drop-last)
        combined   (apply conj (into [] ns-segment) paths)]
    (slurp (io/resource (str/join "/" combined)))))
