(ns streamtide.ui.routes)

(def routes [["/" :route/home]
             ["/admin/announcements" :route.admin/announcements]
             ["/admin/black-listing" :route.admin/black-listing]
             ["/admin/grant-approval-feed" :route.admin/grant-approval-feed]
             ["/admin/round/:round" :route.admin/round]
             ["/admin/rounds" :route.admin/rounds]
             ["/artists" :route.grants/index]
             ["/leaderboard" :route.leaderboard/index]
             ["/my-content" :route.my-content/index]
             ["/my-settings" :route.my-settings/index]
             ["/about" :route.about/index]
             ["/oauth-callback-verifier" :route.oauth-callback/verifier]
             ["/profile/:address" :route.profile/index]
             ["/profile" :route.profile/index]
             ["/feeds" :route.feeds/index]
             ["/send-support" :route.send-support/index]])
