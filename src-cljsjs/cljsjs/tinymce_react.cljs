(ns cljsjs.tinymce-react
  (:require ["@tinymce/tinymce-react" :as tinymce]))

(js/goog.exportSymbol "editor" tinymce)