package boda.alumno.com.mybluetooth20;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.File;
import java.sql.Date;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Hermann on 21/03/2017.
 */

public class FileChooserX extends ListActivity {

    private File currentDir;
    private FileArrayAdapterX adapter;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentDir = new File("/sdcard/ArmandoBluetooth/");
        fill(currentDir);
    }
    private void fill(File f)
    {
        File[]dirs = f.listFiles();
        this.setTitle("Directorio Actual: "+  f.getAbsolutePath()); //f.getName());
        List<ItemX> dir = new ArrayList<ItemX>();
        List<ItemX>fls = new ArrayList<ItemX>();
        try{
            for(File ff: dirs)
            {
                Date lastModDate = new Date(ff.lastModified());
                DateFormat formater = DateFormat.getDateTimeInstance();
                String date_modify = formater.format(lastModDate);

                fls.add(new ItemX(ff.getName(),ff.length() + " Byte", date_modify, ff.getAbsolutePath(),"file_icon"));

            }
        }catch(Exception e)
        {

        }
        Collections.sort(dir);
        Collections.sort(fls);
        dir.addAll(fls);
        if(!f.getName().equalsIgnoreCase("ArmandoBluetooth"))
            dir.add(0,new ItemX("..","Parent Directory","",f.getParent(),"directory_up"));
        adapter = new FileArrayAdapterX(FileChooserX.this,R.layout.file_view2,dir);
        this.setListAdapter(adapter);
    }
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // TODO Auto-generated method stub
        super.onListItemClick(l, v, position, id);
        ItemX o = adapter.getItem(position);
        if(o.getImage().equalsIgnoreCase("directory_icon")||o.getImage().equalsIgnoreCase("directory_up")){
            currentDir = new File(o.getPath());
            fill(currentDir);
        }
        else
        {
            onFileClick(o);
        }
    }
    private void onFileClick(ItemX o) {
        //Toast.makeText(this, "Folder Clicked: "+ currentDir, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent();
        intent.putExtra("GetPath",currentDir.toString());

        //intent.putExtra("GetFileName",o.getName());
        intent.putExtra("GetFileName",o.getPath());
        setResult(RESULT_OK, intent);
        finish();
    }

    private void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
        String value = (String)adapter.getItemAtPosition(position);


    }

}
