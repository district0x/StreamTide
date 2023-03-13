(ns streamtide.ui.routes)

(def routes [["/" :route/home]
             ["/admin/announcements" :route.admin/announcements]
             ["/admin/black-listing" :route.admin/black-listing]
             ["/admin/grant-approval-feed" :route.admin/grant-approval-feed]
             ["/grants" :route.grants/index]
             ["/leaderboard" :route.leaderboard/index]
             ["/my-settings" :route.my-settings/index]
             ["/my-settings-grants" :route.my-settings-grants/index]
             ["/profile/:address" :route.profile/index]
             ["/profile" :route.profile/index]
             ["/send-support" :route.send-support/index]])
