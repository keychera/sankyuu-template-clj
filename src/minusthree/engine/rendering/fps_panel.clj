(ns minusthree.engine.rendering.fps-panel
  (:import
   (imgui ImGui ImVec2)))

(def fps-buf (atom (float-array 40)))
(def fps-idx (atom 0))
(def last-sample-time (atom 0))

(defn sample-fps [fps]
  (let [now (System/currentTimeMillis)]
    (when (>= (- now @last-sample-time) 1000)
      (let [buf @fps-buf]
        (aset-float buf @fps-idx (float fps))
        (swap! fps-idx #(mod (inc %) (alength buf)))
        (reset! last-sample-time now)))))

(defn render!
  [title text]
  (let [fps (.getFramerate (ImGui/getIO))]
    (when (ImGui/begin (or title "fps panel"))
      (ImGui/text (or text "nothing special here"))
      (ImGui/text (format "FPS: %.1f (%.2f ms)" fps (if (pos? fps) (/ 1000.0 fps) 0.0)))
      (sample-fps fps)
      (let [buf  @fps-buf size (ImVec2. (float 240) (float 120))]
          ;; label values count offset overlay scaleMin scaleMax size  
        (ImGui/plotLines "" buf (alength buf) @fps-idx "FPS graph" (float 0.0) (float 180.0) size)))
    (ImGui/end)))
