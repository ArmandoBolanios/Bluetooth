package boda.alumno.com.mybluetooth20;

import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "java.boda.alumno.com.mybluetooth20.MainActivity";
            //src\main\java\boda\alumno\com\mybluetooth20
    //constante para lanzar los Intent de activacion de Bluetooth
    private static final int REQUEST_ENABLE_BT = 1;
    private static final String ALERTA         = "alerta";

    //controles de la actividad
    private Button btnEnviar;
    private Button btnBluetooth;
    private Button btnBuscarDispositivo;
    private Button btnConectarDispositivo;
    private Button btnSalir;
    private EditText edtMensaje;
    private TextView tvMensaje;
    private TextView tvConexion;
    private ListView lvDispositivos;

    private BluetoothAdapter bthAdapter;
    private ArrayList<BluetoothDevice> arrayDevices; //listado de dispositivos
    private ArrayAdapter arrayAdapter;
    private Set<BluetoothDevice> dispositivosVinculados; //lista de dispositivos vinculados

    private BluetoothService servicio; //servicio de mensajes de Bluetooth
    private BluetoothDevice ultimoDispositivo; //ultimo dispositivo conectado

    private static final int REQUEST_PATH = 2;  //obtiene la ruta del archivo
    String curFileName;
    private Button btnSearchFile;
    private Button btnListaArchivos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //invocamos el metodo de configuracion de nuestros controles
        configurarControles();
    }
    //termina metodo onCreate

    private final BroadcastReceiver bcReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            //STATE_OFF - el bluetoothse desactiva
            //ESTATE_ON - el bluetooth se activa
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int estado = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);

                //saber si el Bluetooth se encuentra activo o ne
                //unicamente cambiará el texto del botón dependiendo de si esta
                //activo o ne...
                switch (estado) {
                    //si esta apagado
                    case BluetoothAdapter.STATE_OFF:
                    {
                        Log.v(TAG,"onReceive: Apagando");
                        ((Button)findViewById(R.id.btnBluetooth)).setText(R.string.ActivarBluetooth);
                        ((Button)findViewById(R.id.btnBuscarDispositivo)).setEnabled(false);
                        ((Button)findViewById(R.id.btnConectarDispositivo)).setEnabled(false);
                        break;
                    }
                    //termina caso OFF

                    //si esta encendido
                    case BluetoothAdapter.STATE_ON:
                    {
                        Log.v(TAG,"onReceive: Encendiendo");
                        ((Button)findViewById(R.id.btnBluetooth)).setText(R.string.DesactivarBluetooth);
                        ((Button)findViewById(R.id.btnBuscarDispositivo)).setEnabled(true);
                        ((Button)findViewById(R.id.btnConectarDispositivo)).setEnabled(true);

                        //se lanza un Intent para visibilidad Bluetooth con un valor de 150 segundos
                        Intent visibilidad = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                        visibilidad.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 150);
                        startActivity(visibilidad);
                        break;
                    }
                    //termina caso ON
                    default:
                        break;
                } //termina el switch

            } //termina condicion if

            //cada vez que se descubra un nuevo dispositivo por el Bluetooth, se ejecutara lo siguiente:
            else if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                //acciones a realizar al descubir un nuevo dispositivo
                if(arrayDevices == null)
                    arrayDevices = new ArrayList<BluetoothDevice>();
                //extraer el dispositivo del intent
                BluetoothDevice dispositivo = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //AÑADIR dispositivo al array
                arrayDevices.add(dispositivo);
                //asignamos un nombre del destino
                String descripcionDispos = dispositivo.getName() + " [" + dispositivo.getAddress()
                        + "]";

                //mostrar el dispositivo encontrado por el Toast
                Toast.makeText(getBaseContext(), getString(R.string.DetectadoDispositivo)
                        +": " + descripcionDispos, Toast.LENGTH_SHORT).show();
                Log.v(TAG, "ACTION_FOUND: Dispositivo encontrado: " + descripcionDispos);
            } //termina else if

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                //instaciar nuevo adapter para el ListView
                arrayAdapter = new BluetoothDeviceArrayAdapter(getBaseContext(),
                        android.R.layout.simple_list_item_2, arrayDevices);
                lvDispositivos.setAdapter(arrayAdapter);
                Toast.makeText(getBaseContext(), "Fin de la Búsqueda", Toast.LENGTH_SHORT).show();
            } //termina else if
        }
        //termina onReceive
    };
    //termina Broadcast


    //Handler que obtendrá informacion de BluetoothService
    private final Handler handler = new Handler() {

        @Override
        public void handleMessage (Message msg) {
            byte[] buffer = null;
            String mensaje = null;

            //tipo de mensaje
            switch (msg.what) {
                //mensaje de lectura: se mostrará en el TextView
                case BluetoothService.MSG_LEER:
                {
                    buffer = (byte[]) msg.obj;
                    mensaje = new String(buffer, 0, msg.arg1);
                    tvMensaje.setText(mensaje);
                    break;
                } //fin caso 1

                //mensaje de escritura: se mostrará en el Toast
                case BluetoothService.MSG_ESCRIBIR:
                {
                    buffer = (byte[]) msg.obj;
                    mensaje = new String(buffer);
                    mensaje = getString(R.string.EnviandoMensaje) + ": " + mensaje;
                    Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                    break;
                } //fin caso 2

                //mensaje de cambio de estado
                case BluetoothService.MSG_CAMBIO_ESTADO:
                {
                    switch(msg.arg1) {
                        case BluetoothService.ESTADO_ATENDIENDO_PETICIONES:
                            break;

                        //CONECTADO: muestra el dispositivo al que se ha conectado y se activa el botn enviar
                        case BluetoothService.ESTADO_CONECTADO:
                        {
                            mensaje = getString(R.string.ConexionActual) + " " + servicio.getNombreDispositivo();
                            Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                            tvConexion.setText(mensaje);
                            btnEnviar.setEnabled(true);
                            //btnSearchFile.setEnabled(true);
                            //edtMensaje.setEnabled(true); //**
                            break;
                        } //termina caso

                        //REALIZANDO CONEXION- se muestra el dispositivo al que se esta conectando
                        case BluetoothService.ESTADO_REALIZANDO_CONEXION:
                        {
                            mensaje = getString(R.string.ConectandoA) + " " + ultimoDispositivo.getName() + " [" + ultimoDispositivo.getAddress() + "]";
                            Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                            btnEnviar.setEnabled(false);
                            //btnSearchFile.setEnabled(false);
                            //edtMensaje.setEnabled(false); //**
                            break;
                        } //termina caso

                        //NINGUNO - mensaje por defecto, desactivacion del boton de enviar
                            case BluetoothService.ESTADO_NINGUNO:
                            {
                                mensaje = getString(R.string.SinConexion);
                                Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                                tvConexion.setText(mensaje);
                                btnEnviar.setEnabled(false);
                                //btnSearchFile.setEnabled(false);
                                //edtMensaje.setEnabled(false); //**
                                break;
                            } //termina caso
                            default:
                                break;
                    } //termina switch arg1
                    break;
                } //termina case CAMBIO_ESTADO

                //mensaje de alerta: se mosrtara en el Toast
                    case BluetoothService.MSG_ALERTA:
                    {
                        mensaje = msg.getData().getString(ALERTA);
                        Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                        break;
                    }
                    default:
                        break;
            } //termina switch what
        } //termina hanldeMessage

    }; //termina Handler


    public void conectarDispositivo(String direccion) {
        Toast.makeText(this, "Conectando a " + direccion, Toast.LENGTH_LONG).show();
        if(servicio != null) {
            BluetoothDevice dispositivoReomoto = bthAdapter.getRemoteDevice(direccion);
            servicio.solicitarConexion(dispositivoReomoto);
            this.ultimoDispositivo = dispositivoReomoto;
        }
    } //termina conectarDispositivo


    private AlertDialog crearDialogoConexion(String titulo, String mensaje, final String direccion) {
        //instanciamos un nuevo alert dialog Builder y le asociamos titulo y mensaje
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle(titulo);
        alertDialogBuilder.setMessage(mensaje);

        //creamos un nuevo OnClickListener para el boton OK
        DialogInterface.OnClickListener listenerOk = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                conectarDispositivo(direccion);
            } //termina onClick
        }; //termina DialogInterface

        //nuevo OnClickListener boton Cancelar
        DialogInterface.OnClickListener listenerCancelar = new DialogInterface.OnClickListener() {
          @Override
            public void onClick(DialogInterface dialog, int which) {
              return;
          } //termina onClick
        }; //termina DialogInterface

        //asignamos los botones positivo y negativo a sus respectivos listeners
        alertDialogBuilder.setPositiveButton(R.string.Conectar, listenerOk);
        alertDialogBuilder.setNegativeButton(R.string.Cancelar, listenerCancelar);

        return alertDialogBuilder.create();
    } //termina AlertDialog


    private void configurarAdaptadorBluetooth() {
        bthAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bthAdapter == null) {
            btnBluetooth.setEnabled(false);
            return;
        }

        if(bthAdapter.isEnabled()) {
            btnBluetooth.setText(R.string.DesactivarBluetooth);
            btnBuscarDispositivo.setEnabled(true);
            btnConectarDispositivo.setEnabled(true);
        }
        else {
            btnBluetooth.setText(R.string.ActivarBluetooth);
        }
    } //termina configurarAdaptador


    //referencia los elementos de interfaz
    private void referenciarControles() {
        //elementos de interfaz
        btnEnviar = (Button) findViewById(R.id.btnEnviar);
        btnBluetooth = (Button) findViewById(R.id.btnBluetooth);
        btnBuscarDispositivo = (Button) findViewById(R.id.btnBuscarDispositivo);
        btnConectarDispositivo = (Button) findViewById(R.id.btnConectarDispositivo);
        btnSalir = (Button) findViewById(R.id.btnSalir);
        edtMensaje = (EditText) findViewById(R.id.edtMensaje);
        tvMensaje = (TextView) findViewById(R.id.tvMensaje);
        tvConexion = (TextView) findViewById(R.id.tvConexion);
        lvDispositivos = (ListView) findViewById(R.id.lvDispositivos);

        btnSearchFile = (Button) findViewById(R.id.btnArchivo);
        btnListaArchivos = (Button) findViewById(R.id.btnListaArchivos);
    }


    //suscribe el BroadcastReceiver a los eventos relacionado con Bluetooth que
    //queremos controlar
    private void registrarEventosBluetooth() {
        IntentFilter filtro = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filtro.addAction(BluetoothDevice.ACTION_FOUND);
        filtro.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        this.registerReceiver(bcReceiver, filtro);
    } //termina registrarEventosBluetooth


    //configura el ListView para que responda a los eventos de pulsacion
    private void configurarListaDispositivos() {
        lvDispositivos.setOnItemClickListener(new AdapterView.OnItemClickListener(){

            @Override
            public void onItemClick(AdapterView adapter, View view, int position, long arg) {

                //recibir el dispositivo bluetooth y realizar la conexion
                BluetoothDevice dispositivo = (BluetoothDevice) lvDispositivos.getAdapter().getItem(position);

                AlertDialog dialog = crearDialogoConexion(getString(R.string.Conectar),
                        getString(R.string.MsgConfirmarConexion) + " " + dispositivo.getName() + "?",
                        dispositivo.getAddress());
                dialog.show();
            } //termina onItemClick
        });
    }


    //registra los eventos de interfaz .. onClick, onItemClick,etc.
    private void registrarEventosControles() {
        //asigna handlers de los botones
        btnEnviar.setOnClickListener(this);
        btnBluetooth.setOnClickListener(this);
        btnBuscarDispositivo.setOnClickListener(this);
        btnConectarDispositivo.setOnClickListener(this);
        btnSalir.setOnClickListener(this);

        btnSearchFile.setOnClickListener(this);
        btnListaArchivos.setOnClickListener(this);
        //lista de dispositivos
        configurarListaDispositivos();
    }


    private void configurarControles() {
        //instranciamos el array de dispositivos
        arrayDevices = new ArrayList<BluetoothDevice>();

        //referenciamos los controles y registramos los listeners
        referenciarControles();
        registrarEventosControles();

        //por defecto, desactivamos los botonoes que no puedan utilizarse
        btnEnviar.setEnabled(false);
        btnBuscarDispositivo.setEnabled(false);
        btnConectarDispositivo.setEnabled(false);
        //btnSearchFile.setEnabled(false);
        //edtMensaje.setEnabled(false);
        //configurarmos el adaptador Bluetooth y nos suscribimos a sus eventos
        configurarAdaptadorBluetooth();
        registrarEventosBluetooth();

    } //termina configurarControles


    /*public void enviarMensaje(String mensaje) {
        if(servicio.getEstado() != BluetoothService.ESTADO_CONECTADO) {
            Toast.makeText(this, R.string.MsgErrorConexion, Toast.LENGTH_SHORT).show();
            return;
        }
        if(mensaje.length() > 0) {
            byte[] buffer = mensaje.getBytes();
            servicio.enviar(buffer);
        }
    } //termina enviarMensaje */

    public void getFileX(View view){
        Intent intent1 = new Intent(this, FileChooser.class);
        startActivityForResult(intent1,REQUEST_PATH);
    }

    public void getFileY(View view) {
        Intent intent2 = new Intent(this, FileChooserX.class);
        startActivityForResult(intent2, REQUEST_PATH);
    }

    //manejar los eventos onClic de los botones
    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.btnEnviar: {
                if (servicio != null) {
                    //servicio.enviar(edtMensaje.getText().toString().getBytes());
                    servicio.enviarArchivo(edtMensaje.getText().toString());
                    Toast toast1 = Toast.makeText(getApplicationContext(), "Enviando Archivo", Toast.LENGTH_SHORT);
                    toast1.show();
                    edtMensaje.setText("");
                    Toast toast2 = Toast.makeText(getApplicationContext(), "Archivo Enviado", Toast.LENGTH_SHORT);
                    toast2.show();
                }
                break;

            } //termina case btnEnviar

            case R.id.btnArchivo: { // boton raíz
                getFileX(v);
                File carpeta = new File(Environment.getExternalStorageDirectory() +  "/ArmandoBluetooth");
                //si la carpeta no existe, entonces se crea
                if(!carpeta.exists()) {
                    if(carpeta.mkdir());
                }
                else {
                }Log.d("carpeta creada", carpeta.getAbsolutePath());
                break;
            }

            //activar/desactivar Bluettot
            case R.id.btnBluetooth:
            {
                if(bthAdapter.isEnabled()) {
                    if(servicio != null)
                        servicio.finalizarServicio();
                    bthAdapter.disable();
                }
                else {
                    //activacion del Bluettoth
                    Intent enablebtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enablebtIntent, REQUEST_ENABLE_BT);
                }
                break;
            } //termina caso btnBluetooth

            //descubrir nuevos dispositivos
            case R.id.btnBuscarDispositivo:
            {
                arrayDevices.clear();

                //si existe un descrubrimiento en curso
                if(bthAdapter.isDiscovering())
                    bthAdapter.cancelDiscovery();

                //inicia busqueda de dispositivos
                if(bthAdapter.startDiscovery())
                    Toast.makeText(this, R.string.IniciandoDescubrimiento, Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(this, R.string.ErrorIniciandoDescubrimiento, Toast.LENGTH_SHORT).show();
                break;
            } //termina caso btnBuscarDispositivo

            //muestra todos los dispositivos enlazados al dispositivo actual
            case R.id.btnConectarDispositivo:
            {
                Set<BluetoothDevice> dispositivosEnlazados = bthAdapter.getBondedDevices();
                arrayDevices = new ArrayList<BluetoothDevice>(dispositivosEnlazados);
                arrayAdapter = new BluetoothDeviceArrayAdapter(getBaseContext(), android.R.layout.simple_list_item_1, arrayDevices);
                lvDispositivos.setAdapter(arrayAdapter);
                Toast.makeText(getBaseContext(), R.string.FinBusqueda, Toast.LENGTH_SHORT).show();
                break;
            } //termina caso btnConectarDispositivos

            case R.id.btnSalir:
            {
                if(servicio != null)
                    servicio.finalizarServicio();
                finish();
                System.exit(0);
                break;
            }

            case R.id.btnListaArchivos: {
                getFileY(v);
                break;
            }
            default:
                break;
        }
        //termina switch getId
    }
    //termina onClick
/*
    private UUID generarUUID() {
        ContentResolver appResolver = getApplicationContext().getContentResolver();
        String id = Secure.getString(appResolver, Secure.ANDROID_ID);
        final TelephonyManager telMana = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
        final String deviceId = String.valueOf(telMana.getDeviceId());
        final String simSerialNumber = String.valueOf(telMana.getSimSerialNumber());
        final String androidId = Secure.getString(getContentResolver(), Secure.ANDROID_ID);

        UUID uuid = new UUID(androidId.hashCode(), ((long) deviceId.hashCode() <<32) | simSerialNumber.hashCode());

        return uuid;
    } //termina UUID
*/

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode)
        {
            case REQUEST_ENABLE_BT: {
                Log.v(TAG, "onActivityResult: REQUEST_ENABLE_BT");

                if (resultCode == RESULT_OK) {
                    btnBluetooth.setText(R.string.DesactivarBluetooth);

                    if (servicio != null) {
                        servicio.finalizarServicio();
                        servicio.iniciarServicio();

                    } else
                        servicio = new BluetoothService(this, handler, bthAdapter);
                }

                break;
            }

            case REQUEST_PATH: {
                    if(resultCode == RESULT_OK) {
                        curFileName = data.getStringExtra("GetFileName");
                        edtMensaje.setText(curFileName);
                    }
                break;
            }

            default:
                break;
        }

    } //termina onActivityResult


    //eliminar registro de Broadcast
    @Override
    public void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(bcReceiver);
        if(servicio != null)
            servicio.finalizarServicio();
    } //termina onDestroy

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(servicio != null) {
            if(servicio.getEstado() == BluetoothService.ESTADO_NINGUNO) {
                servicio.iniciarServicio();
            } //termina if
        } //termina if
    } //termina onResume

    @Override
    public synchronized void onPause() {
        super.onPause();
    } //termina onPause



} //termiina MainActivity