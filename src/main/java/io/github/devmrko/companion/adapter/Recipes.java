package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.BuildRecipe;
import org.springframework.context.annotation.*;

@Configuration
public class Recipes {
    @Bean BuildRecipe maven() { return recipe("maven","Java · Maven","./mvnw -B verify","BINARY","application","target/*.jar"); }
    @Bean BuildRecipe gradle() { return recipe("gradle","Java · Gradle","./gradlew --no-daemon build","BINARY","application","build/libs/*.jar"); }
    @Bean BuildRecipe python() { return recipe("python","Python · pip / pytest","python3 -m venv .venv\n. .venv/bin/activate\npython -m pip install -r requirements.txt\npython -m pip install pytest build\npython -m pytest\npython -m build","BINARY","application","dist/*.whl"); }
    @Bean BuildRecipe node() { return recipe("node","TypeScript · npm","npm ci\nnpm run typecheck --if-present\nnpm test\nnpm run build\ntar -czf application.tar.gz dist","BINARY","application","application.tar.gz"); }
    private BuildRecipe recipe(String id,String label,String command,String type,String artifact,String location) {
        return new BuildRecipe() {
            public String id() { return id; }
            public String label() { return label; }
            public String specification() {
                return "# Generated starter: review tool versions, dependency locks and artifact path.\n"
                    +"# This file is not applied automatically. Commit as build_spec.yaml.\n"
                    +"# For BINARY artifacts, replace wildcard locations with ONE exact output file.\n"
                    +"# Python/Node scripts and test dependencies must match your repository.\n"
                    +"version: 0.1\ncomponent: build\ntimeoutInSeconds: 1200\nshell: bash\nfailImmediatelyOnError: true\n"
                    +"steps:\n  - type: Command\n    name: Build and test\n    command: |\n      set -euo pipefail\n      "
                    +command.replace("\n","\n      ")+"\noutputArtifacts:\n  - name: "+artifact+"\n    type: "+type+"\n    location: \""+location+"\"\n";
            }
        };
    }
}
