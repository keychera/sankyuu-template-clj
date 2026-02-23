(ns minusthree.engine.math
  (:require
   [fastmath.core :as m]
   [fastmath.matrix :as mat]
   [fastmath.vector :as v]
   [fastmath.quaternion :as q])
  (:import [fastmath.matrix Mat4x4]))

;; bootstrapping from thi.ng/geom to generateme/fastmath

(defn translation-mat
  ([xyz]
   (let [[^float tx ^float ty ^float tz] xyz]
     (translation-mat tx ty tz)))
  ([tx ty tz]
   (mat/mat
    1.0  0.0  0.0  0.0
    0.0  1.0  0.0  0.0
    0.0  0.0  1.0  0.0
    tx,  ty,  tz,  1.0)))

(defn scaling-mat
  ([xyz]
   (let [[^float sx ^float sy ^float sz] xyz]
     (scaling-mat sx sy sz)))
  ([sx sy sz]
   (mat/mat
    sx,  0.0  0.0  0.0
    0.0  sy,  0.0  0.0
    0.0  0.0  sz,  0.0
    0.0  0.0  0.0  1.0)))

(defn perspective
  [fovy aspect near far]
  (let [f      (/ (Math/tan (* 0.5 (m/radians fovy))))
        nf     (/ (- near far))]
    (mat/mat
     (/ f aspect) 0.0 0.0 0.0
     0.0 f 0.0 0.0
     0.0 0.0 (* (+ near far) nf) -1.0
     0.0 0.0 (* 2.0 near far nf) 0.0)))

(def eps 1.0e-9)

(defn ortho-normal
  ([[a b c]] (v/normalize (v/cross (v/sub b a) (v/sub c a))))
  ([a b] (v/normalize (v/cross a b)))
  ([a b c] (v/normalize (v/cross (v/sub b a) (v/sub c a)))))

(defn look-at
  [eye target up]
  (let [dir (v/sub eye target)]
    (if (< (v/magsq dir) eps)
      (mat/eye 4)
      (let [[zx zy zz :as z] (v/normalize dir)
            [xx xy xz :as x] (ortho-normal up z)
            [yx yy yz :as y] (ortho-normal z x)]
        (mat/mat
         xx yx zx 0.0
         xy yy zy 0.0
         xz yz zz 0.0
         (- (v/dot x eye))
         (- (v/dot y eye))
         (- (v/dot z eye))
         1.0)))))

(defn quat->mat4 [[^double w ^double x ^double y ^double z]]
  (let [x2 (+ x x)
        y2 (+ y y)
        z2 (+ z z)
        xx (* x x2)
        xy (* x y2)
        xz (* x z2)
        yy (* y y2)
        yz (* y z2)
        zz (* z z2)
        wx (* w x2)
        wy (* w y2)
        wz (* w z2)]
    (mat/mat
     (- 1.0 (+ yy zz)) (+ xy wz) (- xz wy) 0.0
     (- xy wz) (- 1.0 (+ xx zz)) (+ yz wx) 0.0
     (+ xz wy) (- yz wx) (- 1.0 (+ xx yy)) 0.0
     0.0 0.0 0.0 1.0)))

(defn ^:vibe decompose-Mat4x4
  "Return {:translation vec3 :rotation quat :scale vec3}"
  [^Mat4x4 m]
  (let [m00 (.a00 m)
        m01 (.a01 m)
        m02 (.a02 m)
        m10 (.a10 m)
        m11 (.a11 m)
        m12 (.a12 m)
        m20 (.a20 m)
        m21 (.a21 m)
        m22 (.a22 m)
        m30 (.a30 m)
        m31 (.a31 m)
        m32 (.a32 m)

        ;; translation (row-major)
        tx m30
        ty m31
        tz m32

        ;; column lengths = scale
        sx (Math/sqrt (+ (* m00 m00) (* m10 m10) (* m20 m20)))
        sy (Math/sqrt (+ (* m01 m01) (* m11 m11) (* m21 m21)))
        sz (Math/sqrt (+ (* m02 m02) (* m12 m12) (* m22 m22)))

        ;; avoid division by zero
        sx (if (zero? sx) 1.0 sx)
        sy (if (zero? sy) 1.0 sy)
        sz (if (zero? sz) 1.0 sz)

        ;; normalized rotation columns
        r00 (/ m00 sx)  r10 (/ m10 sx)  r20 (/ m20 sx)
        r01 (/ m01 sy)  r11 (/ m11 sy)  r21 (/ m21 sy)
        r02 (/ m02 sz)  r12 (/ m12 sz)  r22 (/ m22 sz)

        ;; rotation-only matrix
        rot-m
        (mat/mat
         r00 r01 r02
         r10 r11 r12
         r20 r21 r22)]

    {:translation (v/vec3 tx ty tz)
     :rotation    (q/from-rotation-matrix rot-m)
     :scale       (v/vec3 sx sy sz)}))
