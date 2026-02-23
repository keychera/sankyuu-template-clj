(ns minusthree.engine.macros
  (:require [odoyle.rules :as o]))

(defmacro s->
  "Thread like `->` but always ends with (odoyle.rules/reset!)."
  [x & forms]
  `(-> ~x ~@forms o/reset!))

(defmacro insert!
  "this is the same as `o/insert!`. 
   the reason this macro exist is because I wanted to try nivekuil's branch of odoyle rules but
   that branch has a breaking change of not having `o/reset` so I wanted my codebase to be ready to receive such change by having this macro.
   That was a while ago. This project still uses vanilla odoyle rules"
  ([[id attr value]]
   `(s-> ~'session (o/insert ~id ~attr ~value)))
  ([id attr->value]
   `(s-> ~'session (o/insert ~id ~attr->value)))
  ([id attr value]
   `(s-> ~'session (o/insert ~id ~attr ~value))))

(defmacro vars->map [& vars]
  (zipmap (map (comp keyword name) vars) vars))
