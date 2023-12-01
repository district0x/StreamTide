(ns cljsjs.jwt-decode
  (:require ["jwt-decode" :default jd]))

(js/goog.exportSymbol "jwt_decode" jd)
