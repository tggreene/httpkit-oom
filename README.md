# httpkit.oom

This is a minimal replication of a direct memory issue in httpkit, which causes
the jvm to throw "OutOfMemoryError: Direct buffer memory" exceptions.

Please refer to [test/httpkit/oom_test.clj](test/httpkit/oom_test.clj) for
implementations of circumstances that lead to this issue.
