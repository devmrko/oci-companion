package io.github.devmrko.companion;
import io.github.devmrko.companion.port.BuildControl.Parameter;
import io.github.devmrko.companion.port.BuildInputStore.Rule;
import io.github.devmrko.companion.service.BuildInputs;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BuildInputsTest {
    final List<Parameter> current=List.of(new Parameter("MODE","verify","빌드 모드"));
    @Test void requiredAndSelectAreValidatedOnServer() {
        var rules=List.of(new Rule("MODE","select","required",List.of("verify","package")));
        assertDoesNotThrow(()->BuildInputs.validate(BuildInputs.fields(current,current,rules)));
        assertThrows(RuntimeException.class,()->BuildInputs.validate(BuildInputs.fields(current,List.of(new Parameter("MODE","","")),rules)));
        assertThrows(RuntimeException.class,()->BuildInputs.validate(BuildInputs.fields(current,List.of(new Parameter("MODE","arbitrary-command","")),rules)));
    }
    @Test void noRuleDoesNotInventRequiredConstraintAndStalePresetIsRejected() {
        assertEquals("필수 여부 미지정",BuildInputs.fields(current,current,List.of()).getFirst().badge());
        assertThrows(RuntimeException.class,()->BuildInputs.fields(current,List.of(new Parameter("DELETED","x","")),List.of()));
        assertThrows(RuntimeException.class,()->BuildInputs.rules(current,List.of("OTHER"),List.of("text"),List.of("required"),List.of("")));
        assertThrows(RuntimeException.class,()->BuildInputs.rules(current,List.of("MODE"),List.of("select"),List.of("required"),List.of("")));
    }
    @Test void ruleOptionsRemainSeparateValues() {
        var rules=BuildInputs.rules(current,List.of("MODE"),List.of("select"),List.of("required"),List.of("verify\npackage\nverify"));
        assertEquals(List.of("verify","package"),rules.getFirst().choices());
    }
}
