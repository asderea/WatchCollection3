"""Android SDK 없이 JavaCompiler.parse()를 사용해 전체 Java 소스를 구문 분석합니다."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

JAVA = r'''
import java.io.File;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;

public class ParseAndroidSources {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> errors=new DiagnosticCollector<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(errors,null,null)) {
            List<File> sources=new ArrayList<>();
            for(String path:args) sources.add(new File(path));
            Iterable<? extends JavaFileObject> files=manager.getJavaFileObjectsFromFiles(sources);
            JavacTask task=(JavacTask)compiler.getTask(null,manager,errors,
                    Collections.singletonList("-proc:none"),null,files);
            task.parse();
            int failed=0;
            for(Diagnostic<? extends JavaFileObject> d:errors.getDiagnostics()) {
                if(d.getKind()==Diagnostic.Kind.ERROR) {
                    System.err.println(d);
                    failed++;
                }
            }
            if(failed>0) System.exit(1);
            System.out.println("PASS: Parsed "+sources.size()+" Java source files");
        }
    }
}
'''


class JavaSyntaxTest(unittest.TestCase):
    def test_all_production_java_parses(self):
        files=list((ROOT/'app/src/main/java').rglob('*.java'))
        self.assertGreater(len(files),20)
        with tempfile.TemporaryDirectory() as t:
            source=Path(t)/'ParseAndroidSources.java'
            source.write_text(JAVA)
            subprocess.run(['javac',str(source)],check=True,capture_output=True,text=True)
            outcome=subprocess.run(['java','-cp',t,'ParseAndroidSources']+[str(f) for f in files],
                    check=True,capture_output=True,text=True)
            self.assertIn('PASS:',outcome.stdout)


if __name__=='__main__':
    unittest.main(verbosity=2)
