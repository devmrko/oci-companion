package io.github.devmrko.companion.port;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.service.TestReports;
import java.util.List;

/** Report objects stay in the cloud; the application keeps no copy. */
public interface ReportStorage {
    record Options(String namespace, List<String> buckets) {}
    Options options(Connection connection, String region, String compartment);
    void createBucket(Connection connection, String region, String compartment, String name);
    List<String> objects(Connection connection, String region, String compartment, String bucket, String prefix);
    TestReports.Summary read(Connection connection, String region, String compartment, String bucket, String object);
}
