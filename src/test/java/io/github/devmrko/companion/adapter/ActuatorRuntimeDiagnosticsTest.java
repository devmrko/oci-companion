package io.github.devmrko.companion.adapter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class ActuatorRuntimeDiagnosticsTest {
 final JsonMapper json=JsonMapper.builder().build();
 @Test void cumulativeStatisticsAreNotInventedPercentiles(){var rows=ActuatorRuntimeDiagnostics.parseMetric("http.server.requests",json.readTree("{\"name\":\"http.server.requests\",\"baseUnit\":\"seconds\",\"measurements\":[{\"statistic\":\"COUNT\",\"value\":10},{\"statistic\":\"TOTAL_TIME\",\"value\":2}]}"));assertThat(rows).hasSize(2);assertThat(rows.getFirst().unit()).isEqualTo("count");assertThat(rows.getLast().unit()).isEqualTo("seconds");assertThat(rows).noneMatch(m->m.statistic().contains("95"));}
 @Test void wrongMetricAndInvalidValuesAreNotAccepted(){assertThatThrownBy(()->ActuatorRuntimeDiagnostics.parseMetric("jvm.memory.used",json.readTree("{\"name\":\"other\",\"measurements\":[]}"))).isInstanceOf(IllegalArgumentException.class);assertThat(ActuatorRuntimeDiagnostics.parseMetric("jvm.memory.used",json.readTree("{\"name\":\"jvm.memory.used\",\"measurements\":[{\"statistic\":\"VALUE\",\"value\":-1}]}"))).isEmpty();}
 @Test void dumpProjectsOnlyStackFieldsAndRejectsOtherPayloads(){var dump=ActuatorRuntimeDiagnostics.parseDump("app",json.readTree("{\"password\":\"do-not-copy\",\"threads\":[{\"threadName\":\"worker\",\"threadId\":12,\"threadState\":\"WAITING\",\"stackTrace\":[{\"className\":\"example.App\",\"methodName\":\"run\",\"fileName\":\"App.java\",\"lineNumber\":42}]}]}"));assertThat(dump.text()).contains("worker #12 WAITING","example.App.run(App.java:42)").doesNotContain("password","do-not-copy");assertThatThrownBy(()->ActuatorRuntimeDiagnostics.parseDump("app",json.readTree("{\"threads\":[]}"))).isInstanceOf(IllegalArgumentException.class);}
}
