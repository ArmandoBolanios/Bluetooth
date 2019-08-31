package boda.alumno.com.mybluetooth20;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Hermann on 05/02/2017.
 */

public class BluetoothDeviceArrayAdapter extends ArrayAdapter {

    private List<BluetoothDevice> deviceList; //contendra el listado de dispositivos
    private Context context;

    public BluetoothDeviceArrayAdapter(Context context, int textViewResourceId,
                                       List<BluetoothDevice> objects) {
        super(context, textViewResourceId, objects);
        this.deviceList = objects;
        this.context = context;
    }

    @Override
    public int getCount()
    {
        if(deviceList != null)
            return deviceList.size();
        else
            return -1;
    }

    @Override
    public Object getItem(int position)
    {
        return (deviceList == null ? null : deviceList.get(position));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        if((deviceList == null) || (context == null))
            return null;

        // Usamos un LayoutInflater para crear las vistas
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Creamos una vista a partir de simple_list_item_2, que contiene dos TextView.
        // El primero (text1) lo usaremos para el nombre, mientras que el segundo (text2)
        // lo utilizaremos para la direccion del dispositivo.
        View elemento = inflater.inflate(android.R.layout.simple_list_item_2, parent, false);

        // Referenciamos los TextView
        TextView txtNombre = (TextView)elemento.findViewById(android.R.id.text1);
        TextView txtDireccion = (TextView)elemento.findViewById(android.R.id.text2);

        // Obtenemos el dispositivo del array y obtenemos su nombre y direccion, asociandosela
        // a los dos TextView del elemento
        BluetoothDevice dispositivo = (BluetoothDevice)getItem(position);
        if(dispositivo != null)
        {
            txtNombre.setText(dispositivo.getName());
            txtDireccion.setText(dispositivo.getAddress());
        }
        else
        {
            txtNombre.setText("ERROR");
        }

        // Devolvemos el elemento con los dos TextView cumplimentados
        return elemento;
    }

}
