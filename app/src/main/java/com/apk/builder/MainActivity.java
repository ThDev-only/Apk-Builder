
package com.apk.builder;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.apk.builder.compiler.CompilerTask;
import com.apk.builder.databinding.ActivityMainBinding;
import com.apk.builder.logger.Logger;
import com.apk.builder.model.Library;
import com.apk.builder.model.Project;
import java.io.File;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate and get instance of binding
        binding = ActivityMainBinding.inflate(getLayoutInflater());

        binding.build.setOnClickListener(v ->{
                
                Logger lge = new Logger();
                lge.attach(binding.recycler);
                
            Project project = new Project();
                project.setLogger(lge);
                project.setResourcesFile(new File("/sdcard/Robok/.projects/Jogo Vazio/game/res/"));
                project.setJavaFile(new File("/sdcard/Robok/.projects/Jogo Vazio/game/logic/"));
                project.setManifestFile(new File("/sdcard/Robok/.projects/Jogo Vazio/game/AndroidManifest.xml"));
                project.setMinSdk(21);
                project.setTargetSdk(28);
                project.setLibraries(Library.fromFile(new File("")));
                project.setOutputFile(new File("/sdcard/Robok/.projects/Jogo Vazio/build/"));
                
                CompilerTask task = new CompilerTask(MainActivity.this);
                task.execute(project);
        });
        
        
        // set content view to binding's root
        setContentView(binding.getRoot());
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.binding = null;
    }
}
