# User Stories

- Cheapest product
  - As a user, I want to find the cheapest market to buy a specific product in order to make maximal gains from selling
  - When I run the app with an argument specifying the product I'm looking for
  - Then I see a list of relevant posts, in order of price
- Margin Alert
  - As a user, I want to find opportunities to make money by buying a product on one market, and selling on another
  - When I trigger the Margin Alert functionality
  - Then I see a list of products each with an associated post pair, ordered by the difference in prices of the two posts
- Data Collection
  - As a user, I want to collect a large database of known products, linked to opportunities to buy or sell, in order to facilitate Margin Alert functionality
  - When I trigger the Data Collection functionality on Market m
  - Then posts are scraped from m repeatedly until no more posts can be found, or program is terminated