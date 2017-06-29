## Netflix 6/21/17

### Alerting

* Support triggering on one query, sending alert notifications on another.
One is to discover a problem exists, the other to provide actionable insight
quickly.
* Alert queries are often instance bound, e.g. `:group-by` instance to generate
a signal per instance. Healthy instances will tend to cluster together in a
mass of color, and the bad instance(s) will be visibly divergent.
* Look at `:rolling-count`.

### ACA

* Most canaries run 1-4 hours. Extreme cases run 96 hours. Shorter windows
are recommended, as bad canaries are receiving production traffic -- should
be failed as soon as possible.
* ACA at Netflix is approaching limits on how many queries it is allowed to
perform against Atlas in a given window. To improve, could combine queries
to baseline and canary in one and split the results in ACA. Also, may be
able to use LWC streaming?
* Good canary metrics include box level metrics, error counting metrics with
sufficient volume to present a statistically viable comparison.
* Because a normal distribution cannot be assumed, uses Mann-Whitney U Test.
* Use canary analysis to inform alerts -- if a canary is critically failing, 
even if it only lasts 4 hours, shouldn't somebody have been alerted on 
failures before?
* Good ACA criteria = U.S.E. = Utilization, Saturation, Error (Brendan Gregg)
* Some teams basing ACAs off of high-dimension data stored in Druid.

### Atlas

* Look at `:dist-max` when concerned about the absolute max in a given window.
The `max` statistic shown on a graph is the maximum value that the plot yields,
but this may be the max average value if the plot is `:avg`, for example.
`:dist-max` plots the maximum sample at each step. So, `max` of `:dist-max` is
the maximum sample seen along the plot's x-axis.
* Constant-time lookup function on buckets is important.
* Bucket functions lead to a mergeable quantile approximation.
* There may be a static 276 bucket histogram that leads to good error bounds
on quantile approximation for majority of use cases.
* Standard deviation calculation often exhibits high error bounds because of cliffs:
  * Left-side cliff on payload size that represents minimum header size
  * Right-side cliff on latency that represents HTTP timeout
  * For a latency timer across all endpoints in an app, distribution can be
  wildly non-normal because of different levels of computation and I/O across
  those endpoints.
* Say no to t-digests.
* Counters not decrementable
* Look at `:cq`, `:list`, `:each` for an easy way to tack on additional
criteria from a dashboard-building app without understanding the existing
structure of the query.
* `:dist-avg` does the `totalTime/count` division math for you.
* r3.2xlg with 60GB RAM capable of managing 2M time series over 6 hours.