(ns streamtide.ui.about.page
  "Page showing description of Streamtide"
  (:require
    [district.ui.component.page :refer [page]]
    [streamtide.ui.components.app-layout :refer [app-layout]]))

(defmethod page :route.about/index []
  (fn []
    [app-layout
     [:main.pageSite
      {:id "about"}
      [:div
       [:div.headerGrants
        [:div.container
         [:h1.titlePage "About"]]]
       [:div.container
        "Stream Tide is an "
        [:a {:href "https://github.com/district0x/StreamTide" :target "_blank" :style {:color "Goldenrod"}} "open-source"]
        " patronage tool that operates on Web3 and micro-grants. We host an open-source patronage tool that acts as fuel for creators, with donation matching to maximize the impact of each contribution. Community-directed capital allocation like this serves as a vehicle to help people reach their goals on the Roads of Web3 and beyond. Web3 and Open Source tools like Stream Tide are the “Future Of Work” we envision."]
       [:div.container
        [:h2.titlePage "Mission"]
        "At Stream Tide, our mission is to usher in a new era for creative artists and culture builders by revolutionizing their work through open-source financial tools. So every individual in the creative commons community has the power to thrive and succeed in a platform-agnostic way."]
       [:div.container
        [:h2.titlePage "Vision"]
        "In the realm of arts and culture, it's the individual creators and their vibrant communities that form the lifeblood of creativity. While platforms come and go, the essence of artistry and collaboration remains timeless. Stream Tide emerges not as another platform, but as a dynamic toolset, designed to empower individuals and foster community growth."
        "Built on the principles of Web3 and deeply rooted in open-source ethos, Stream Tide is a hackable foundation. It's not about creating another walled garden; it's about offering tools that can be adapted, reshaped, and reimagined by the community, for the community."
        "The Ethereum community, with its spirit of decentralization and collaboration, has been our guiding light. For those keen on understanding the philosophy that drives Stream Tide, dive into the concepts at "
        [:a {:href "https://www.radicalxchange.org/concepts/plural-funding/" :target "_blank" :style {:color "Goldenrod"}} "Plural Funding"]
        " and "
        [:a {:href "https://wtfisqf.com" :target "_blank" :style {:color "Goldenrod"}} "Quadratic Funding"]
        ". Stream Tide is more than just a tool; it's a movement. We're not here to compete with platforms; we're here to uplift individuals and communities. Together, we're not just supporting the arts; we're reimagining how we engage, collaborate, and thrive in it. "
        "At Stream Tide, people are our priority. In a digital age teeming with platforms, we're taking a step back, focusing on the individual, the creator, the dreamer. Let's co-create, hack, and shape the future of the creative commons."]]]]))
