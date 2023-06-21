(ns streamtide.ui.components.media-embed
  "Embed media from different popular sites.
  It uses some regex to identify the website and re-format the url to embed the media (image/video) in the current page"
  (:require
    [clojure.string :as string]
    [streamtide.ui.config :as config]))

(def supported-video-exts #{"mp4", "webm", "ogg"})


;; Pattern and replacements adjusted from nodebb-plugin-ns-embed
(def matching-rules
  (let [domain (or (:domain config/config-map) (-> js/window .-location .-hostname))]
    [; YouTube
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?(?:youtube\\.com\\/(?!user|channel)\\S*(?:(?:\\/e(?:mbed))?\\/|watch\\?(?:\\S*?&?v\\=))|youtu\\.be\\/)([a-zA-Z0-9_-]{6,11})")
      :replacement "https://www.youtube.com/embed/$1"
      :iframe-params {:width 560
                      :height 315
                      :allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                      :title "YouTube video player"}}
     ; Twitch
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?twitch\\.tv\\/.*\\/v\\/(\\d+)")
      :replacement (str "https://player.twitch.tv/?autoplay=false&video=v$1&parent=" domain)
      :iframe-params {:width 620
                      :height 378}}
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?twitch\\.tv\\/videos\\/(\\d+)")
      :replacement (str "https://player.twitch.tv/?autoplay=false&video=v$1&parent=" domain)
      :iframe-params {:width 620
                      :height 378}}
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?twitch\\.tv(?!.*\\/v\\/)\\/([a-zA-Z0-9_-]+)")
      :replacement (str "https://player.twitch.tv/?autoplay=false&video=v$1&parent=" domain)
      :iframe-params {:width 620
                      :height 378}}
     ; Vimeo
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?vimeo\\.com\\/(?:channels\\/(?:\\w+\\/)?|groups\\/(?:[^\\/]*)\\/videos\\/|)(\\d+)(?:|\\/\\?)")
      :replacement "https://player.vimeo.com/video/$1"}
     ; Vine
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?vine\\.co\\/v\\/([a-zA-Z0-9_-]{6,11})")
      :replacement "https://vine.co/v/$1/embed/simple"}
     ; coub
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?coub\\.com\\/view\\/([a-zA-Z0-9_-]{4,11})")
      :replacement "https://coub.com/embed/$1?muted=false&autostart=false&originalSize=false&hideTopBar=true&startWithHD=true"}
     ; Blender Tube
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?video\\.blender\\.org\\/videos\\/watch\\/([a-zA-Z0-9_-]{4,36})")
      :replacement "//video.blender.org/videos/embed/$1"}
     ; Dailymotion
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?dailymotion\\.com\\/video\\/([a-zA-Z0-9_-]{4,11})")
      :replacement "//www.dailymotion.com/embed/video/$1"}
     ; FramaTube
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?framatube\\.org\\/videos\\/watch\\/([a-zA-Z0-9_-]{4,36})")
      :replacement "//framatube.org/videos/embed/$1"}
     ; MixCloud
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?mixcloud\\.com\\/([a-zA-Z0-9_-]{4,36})\\/([a-zA-Z0-9_-]{4,136})")
      :replacement "https://www.mixcloud.com/widget/iframe/?light=1&hide_artwork=1&feed=%2F$1%2F$2%2F"}
     ; SoundCloud
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?soundcloud\\.com\\/([a-zA-Z0-9_^/-]{4,250})")
      :replacement "https://w.soundcloud.com/player/?url=https://soundcloud.com/$1&color=%23ff5500&auto_play=false&hide_related=false&show_comments=true&show_user=true&show_reposts=false&show_teaser=true&visual=true"}
     ; Spotify
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?open\\.spotify\\.com\\/album\\/([a-zA-Z0-9_-]{4,36})")
      :replacement "https://open.spotify.com/embed/album/$1"}
     ; Twitter
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:twitter\\.com)\\/([^\\/\"\\s]*)\\/statuse?s?\\/([^\\/\"\\s\\?]*)(\\/photo\\/\\d|\\?.*|)")
      :replacement "https://platform.twitter.com/embed/Tweet.html?id=$2"
      :iframe-params {:height 450}}]))

(defn- file-ext [filename]
  (string/lower-case (last (string/split filename "."))))

(defn- supported-video-ext? [ext]
  (contains? supported-video-exts ext))

(defn embed-video [url]
  "Embed a external video in the current page"
  (let [{:keys [:regex :replacement :iframe-params :component :replacement-target]} (first (filter #(re-find (:regex %) url) matching-rules))
        src (when regex (string/replace url regex replacement))]
    (if src
      (if component
        (assoc-in component replacement-target src)
        ;component
        [:iframe
         (merge
           {:width 560
            :height 315
            :src src
            :scrolling "no"
            :frameBorder "0"
            :allowFullScreen true}
           iframe-params)])
      (let [ext (file-ext url)]
        (if (supported-video-ext? ext)
          [:video {:controls true}
                  [:source {:src url :type (str "video/" ext)}]
                  "Your browser does not support the video tag."]
          [:a {:href url :target "_blank"} url])))))
