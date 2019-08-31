package boda.alumno.com.mybluetooth20;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.UUID;

 
public class BluetoothService {
    private static final String TAG = "java.boda.alumno.com.mybluetooth20.BluetoothService";
    private static final boolean DEBUG_MODE		= true;

    private final Handler handler;
    private final Context context;
    private final BluetoothAdapter bthAdapter;

    public static final String NOMBRE_SEGURO = "BluetoothServiceSecure";
    public static final String NOMBRE_INSEGURO = "BluetoothServiceInsecure";
    public static UUID UUID_SEGURO; //= UUID.fromString("org.danigarcia.examples.bluetooth.BluetoothService.Secure");
    public static UUID UUID_INSEGURO; //= UUID.fromString("org.danigarcia.examples.bluetooth.BluetoothService.Insecure");

    public static final int	ESTADO_NINGUNO				= 0;
    public static final int	ESTADO_CONECTADO			= 1;
    public static final int	ESTADO_REALIZANDO_CONEXION	= 2;
    public static final int	ESTADO_ATENDIENDO_PETICIONES= 3;

    public static final int STATE_CONNECTED = ESTADO_CONECTADO;  // now connected to a remote device
    private int mState;
    private int mNewState;

    public static final int MSG_CAMBIO_ESTADO = 10;
    public static final int MSG_LEER = 11;
    public static final int MSG_ESCRIBIR = 12;
    public static final int MSG_ATENDER_PETICIONES = 13;
    public static final int MSG_ALERTA = 14;

    private int 			estado;
    private HiloServidor	hiloServidor	= null;
    private HiloCliente		hiloCliente		= null;
    private HiloConexion	hiloConexion	= null;

    private ServerSocket servidor = null;

    public BluetoothService(Context context, Handler handler, BluetoothAdapter adapter)
    {
        debug("BluetoothService() ", "Iniciando metodo");

        this.context	= context;
        this.handler 	= handler;
        this.bthAdapter = adapter;
        this.estado 	= ESTADO_NINGUNO;

        UUID_SEGURO = generarUUID();
        UUID_INSEGURO = generarUUID();
    }

    private synchronized void setEstado(int estado)
    {
        this.estado = estado;
        handler.obtainMessage(MSG_CAMBIO_ESTADO, estado, -1).sendToTarget();
    }

    public synchronized int getEstado()
    {
        return estado;
    }

    public String getNombreDispositivo() {
        String nombre = "";
        if(estado == ESTADO_CONECTADO)
        {
            if(hiloConexion != null)
                nombre = hiloConexion.getName();
        }

        return nombre;
    }

    // Inicia el servicio, creando un HiloServidor que se dedicara a atender las peticiones
    // de conexion.
    public synchronized void iniciarServicio()
    {
        debug("iniciarServicio() ", "Iniciando metodo");

        // Si se esta intentando realizar una conexion mediante un hilo cliente,
        // se cancela la conexion
        if(hiloCliente != null)
        {
            hiloCliente.cancelarConexion();
            hiloCliente = null;
        }

        // Si existe una conexion previa, se cancela
        if(hiloConexion != null)
        {
            hiloConexion.cancelarConexion();
            hiloConexion = null;
        }

        // Arrancamos el hilo servidor para que empiece a recibir peticiones
        // de conexion
        if(hiloServidor == null)
        {
            hiloServidor = new HiloServidor();
            hiloServidor.start();
        }

        debug("iniciarServicio() ", "Finalizando metodo");
    }

    public void finalizarServicio()
    {
        debug("finalizarServicio() ", "Iniciando metodo");

        if(hiloCliente != null)
            hiloCliente.cancelarConexion();
        if(hiloConexion != null)
            hiloConexion.cancelarConexion();
        if(hiloServidor != null)
            hiloServidor.cancelarConexion();

        hiloCliente = null;
        hiloConexion = null;
        hiloServidor = null;

        setEstado(ESTADO_NINGUNO);

    }

    // Instancia un hilo conector
    public synchronized void solicitarConexion(BluetoothDevice dispositivo)
    {
        debug("solicitarConexion() ", "Iniciando metodo");
        // Comprobamos si existia un intento de conexion en curso.
        // Si es el caso, se cancela y se vuelve a iniciar el proceso
        if(estado == ESTADO_REALIZANDO_CONEXION)
        {
            if(hiloCliente != null)
            {
                hiloCliente.cancelarConexion();
                hiloCliente = null;
            }
        }

        // Si existia una conexion abierta, se cierra y se inicia una nueva
        if(hiloConexion != null)
        {
            hiloConexion.cancelarConexion();
            hiloConexion = null;
        }

        // Se instancia un nuevo hilo conector, encargado de solicitar una conexion
        // al servidor, que sera la otra parte.
        hiloCliente = new HiloCliente(dispositivo);
        hiloCliente.start();

        setEstado(ESTADO_REALIZANDO_CONEXION);
    }

    public synchronized void realizarConexion(BluetoothSocket socket, BluetoothDevice dispositivo)
    {
        debug("realizarConexion() ", "Iniciando metodo");
        hiloConexion = new HiloConexion(socket);
        hiloConexion.start();
    }

    // Sincroniza el objeto con el hilo HiloConexion e invoca a su metodo escribir()
    // para enviar el mensaje a traves del flujo de salida del socket.
    public int enviar(byte[] buffer) {
        debug("enviar()", "Iniciando metodo");
        HiloConexion tmpConexion;

        synchronized(this) {
            if(estado != ESTADO_CONECTADO)
                return -1;
            tmpConexion = hiloConexion;
        }
        tmpConexion.escribir(buffer);
        return buffer.length;

    }


    public void  enviarArchivo(String url) {
        debug("enviarArchivo()", "Iniciando metodo");
        HiloConexion tmpConexion;
        tmpConexion = hiloConexion;

        tmpConexion.escribirArchivo(url);

    }

    // Hilo que hace las veces de servidor, encargado de escuchar conexiones entrantes y
    // crear un hilo que maneje la conexion cuando ello ocurra.
    // La otra parte debera solicitar la conexion mediante un HiloCliente.
    private class HiloServidor extends Thread
    {
        private final BluetoothServerSocket serverSocket;

        public HiloServidor()
        {
            debug("HiloServidor.new() ", "Iniciando metodo");
            BluetoothServerSocket tmpServerSocket = null;

            // Creamos un socket para escuchar las peticiones de conexion
            try {
                tmpServerSocket = bthAdapter.listenUsingRfcommWithServiceRecord(NOMBRE_SEGURO, UUID_SEGURO);
            } catch(IOException e) {
                Log.e(TAG, "HiloServidor(): Error al abrir el socket servidor", e);
            }

            serverSocket = tmpServerSocket;
        }

        public void run()
        {
            debug("HiloServidor.run()", "Iniciando metodo");
            BluetoothSocket socket = null;

            setName("HiloServidor");
            setEstado(ESTADO_ATENDIENDO_PETICIONES);
            // El hilo se mantendra en estado de espera ocupada aceptando conexiones
            // entrantes siempre y cuando no exista una conexion activa.
            // En el momento en el que entre una nueva conexion,
            while(estado != ESTADO_CONECTADO)
            {
                try {
                    // Cuando un cliente solicite la conexion se asignara valor al socket..
                    socket = serverSocket.accept();
                }
                catch(IOException e) {
                    Log.e(TAG, "HiloServidor.run(): Error al aceptar conexiones entrantes", e);
                    break;
                }

                // Si el socket tiene valor sera porque un cliente ha solicitado la conexion
                if(socket != null)
                {
                    // Realizamos un lock del objeto
                    synchronized(BluetoothService.this)
                    {
                        switch(estado)
                        {
                            case ESTADO_ATENDIENDO_PETICIONES:
                            case ESTADO_REALIZANDO_CONEXION:
                            {
                                debug("HiloServidor.run()", estado == ESTADO_ATENDIENDO_PETICIONES ? "Atendiendo peticiones" : "Realizando conexion");
                                // Estado esperado, se crea el hilo de conexion que recibira
                                // y enviara los mensajes
                                realizarConexion(socket, socket.getRemoteDevice());
                                break;
                            }
                            case ESTADO_NINGUNO: break;
                            case ESTADO_CONECTADO:
                            {
                                // No preparado o conexion ya realizada.
                                // Se cierra el nuevo socket.
                                try {
                                    debug("HiloServidor.run() ", estado == ESTADO_NINGUNO ? "Ninguno" : "Conectado");
                                    socket.close();
                                }
                                catch(IOException e) {
                                    Log.e(TAG, "HiloServidor.run(): socket.close(). Error al cerrar el socket.", e);
                                }
                                break;
                            }
                            default:
                                break;
                        }
                    }
                }

            } // termina while
        } //termina run

        public void cancelarConexion()
        {
            debug("HiloServidor.cancelarConexion()", "Iniciando metodo");
            try {
                serverSocket.close();
            }
            catch(IOException e) {
                Log.e(TAG, "HiloServidor.cancelarConexion(): Error al cerrar el socket", e);
            }
        }
    } //termina HilServidor

    // Hilo encargado de solicitar una conexion a un dispositivo que este corriendo un
    // HiloServidor.
    private class HiloCliente extends Thread
    {
        private final BluetoothDevice dispositivo;
        private final BluetoothSocket socket;

        public HiloCliente(BluetoothDevice dispositivo)
        {
            debug("HiloCliente.new()", "Iniciando metodo");
            BluetoothSocket tmpSocket = null;
            this.dispositivo = dispositivo;

            // Obtenemos un socket para el dispositivo con el que se quiere conectar
            try {
                tmpSocket = dispositivo.createRfcommSocketToServiceRecord(UUID_SEGURO);
            }
            catch(IOException e) {
                Log.e(TAG, "HiloCliente.HiloCliente(): Error al abrir el socket", e);
            }

            socket = tmpSocket;
        }

        public void run()
        {
            debug("HiloCliente.run()", "Iniciando metodo");
            setName("HiloCliente");
            if(bthAdapter.isDiscovering())
                bthAdapter.cancelDiscovery();

            try {
                socket.connect();
                setEstado(ESTADO_REALIZANDO_CONEXION);
            }
            catch(IOException e) {
                Log.e(TAG, "HiloCliente.run(): socket.connect(): Error realizando la conexion", e);
                try {
                    socket.close();
                }
                catch(IOException inner) {
                    Log.e(TAG, "HiloCliente.run(): Error cerrando el socket", inner);
                }
                setEstado(ESTADO_NINGUNO);
            }

            // Reiniciamos el hilo cliente, ya que no lo necesitaremos mas
            synchronized(BluetoothService.this)
            {
                hiloCliente = null;
            }

            // Realizamos la conexion
            realizarConexion(socket, dispositivo);
            /*hiloConexion = new HiloConexion(socket);
            hiloConexion.start();*/
        }

        public void cancelarConexion()
        {
            debug("cancelarConexion()", "Iniciando metodo");
            try {
                socket.close();
            }
            catch(IOException e) {
                Log.e(TAG, "HiloCliente.cancelarConexion(): Error al cerrar el socket", e);
            }
            setEstado(ESTADO_NINGUNO);
        }
    }

    // Hilo encargado de mantener la conexion y realizar las lecturas y escrituras
    // de los mensajes intercambiados entre dispositivos.
    private class HiloConexion extends Thread {

        private final BluetoothSocket 	socket;			// Socket
        private final InputStream		inputStream;	// Flujo de entrada (lecturas)
        private final OutputStream		outputStream;	// Flujo de salida (escrituras)

        public HiloConexion(BluetoothSocket socket) {
            debug("HiloConexion.new()", "Iniciando metodo");
            this.socket = socket;

            setName(socket.getRemoteDevice().getName() + " [" + socket.getRemoteDevice().getAddress() + "]");

            // Se usan variables temporales debido a que los atributos se declaran como final
            // no seria posible asignarles valor posteriormente si fallara esta llamada
            InputStream tmpInputStream = null;
            OutputStream tmpOutputStream = null;

            // Obtenemos los flujos de entrada y salida del socket.
            try {
                tmpInputStream = socket.getInputStream();
                tmpOutputStream = socket.getOutputStream();
            }
            catch(IOException e){
                Log.e(TAG, "HiloConexion(): Error al obtener flujos de E/S", e);
            }

            inputStream = tmpInputStream;
            outputStream = tmpOutputStream;
        }


        //******************************************************************************************
        public void escribir(byte[] buffer) {
            debug("HiloConexion.escribir()", "Iniciando metodo");
            try {

                DataOutputStream dos = new DataOutputStream( outputStream );

                // Escribimos en el flujo de salida del socket
                outputStream.write(buffer);
                // Enviamos la informacion a la actividad a traves del handler.
                // El metodo handleMessage sera el encargado de recibir el mensaje
                // y mostrar los datos enviados en el Toast
                handler.obtainMessage(MSG_ESCRIBIR, -1, -1, buffer).sendToTarget();
            }
            catch(IOException e) {
                Log.e(TAG, "HiloConexion.escribir(): Error al realizar la escritura", e);
            }
        }

        //ESTO ES PARA ENVIAR TEXTO
        public void escribirArchivo(String ruta) {
            debug("HiloConexion.escribirArchivo()", "Iniciando metodo");
            try {
                File archivo = new File(ruta);

                int tamanioArchivo = ( int )archivo.length();
                DataOutputStream dos = new DataOutputStream( outputStream );
                System.out.println( "Enviando Archivo: "+archivo.getName() );
                // Enviamos el nombre del archivo


                dos.writeUTF(archivo.getName());

                dos.flush();
                // Enviamos el tamaño del archivo
                dos.writeInt(tamanioArchivo);

                dos.flush();
                // Creamos flujo de entrada para realizar la lectura del archivo en bytes
                FileInputStream fis = new FileInputStream(archivo);
                BufferedInputStream bis = new BufferedInputStream(fis);


                // Creamos el flujo de salida para enviar los datos del archivo en bytes
                BufferedOutputStream bos = new BufferedOutputStream(outputStream);

                // Creamos un array de tipo byte con el tamaño del archivo
                byte[] buffer = new byte[ tamanioArchivo ];
                int in;

                while (( in = bis.read(buffer)) != -1){
                    bos.write(buffer,0,in);
                }


                bos.flush();

                System.out.println( "Archivo Enviado: "+archivo.getName() );
                Log.e(TAG, "Archivo Enviado: "+archivo.getName());

            }catch(IOException error) {
                Log.e(TAG, "HiloConexion.escribirArchivo(): Error al realizar la escritura" ,error);
            }
        }
        //******************************************************************************************

        // Metodo principal del hilo, encargado de realizar las lecturas
        public void run() {
            debug("HiloConexion.run()", "Iniciando metodo");
            //  byte[] buffer = new byte[1024];
            byte[] buffer;
            //  int bytes;
            setEstado(ESTADO_CONECTADO);
            // Mientras se mantenga la conexion el hilo se mantiene en espera ocupada
            // leyendo del flujo de entrada

            while(true) try {
                // Leemos del flujo de entrada del socket
                //bytes = inputStream.read(buffer); comentado *
                // Creamos flujo de entrada para leer los datos que envia el cliente


                //recibir/leer
                String nombreArchivo; // = dis.readUTF();
                int tam;

                DataInputStream dis = new DataInputStream(inputStream);
                FileOutputStream fos;
                BufferedOutputStream bos;
                BufferedInputStream bis;
                //obtendremos el nombre del fila
                nombreArchivo = dis.readUTF();

                //tamano del archivo
                tam = dis.readInt();

                String tarjeta = Environment.getExternalStorageDirectory().getPath();

                File file = new File(tarjeta+"/ArmandoBluetooth/"+getNombreDispositivo()+ ""+nombreArchivo);

                fos = new FileOutputStream(file);
                bos = new BufferedOutputStream(fos);
                bis = new BufferedInputStream(inputStream);

                buffer = new byte[tam];
                for (int i = 0; i < buffer.length; i++) {
                    buffer[i] = (byte) bis.read();
                }

                //escribimos el archivo
                bos.write(buffer);
                //cerramos el flujo
                bos.flush();
                Log.v(TAG,"run: Archivo recibido");

                // Enviamos la informacion a la actividad a traves del handler.
                // El metodo handleMessage sera el encargado de recibir el mensaje
                // y mostrar los datos recibidos en el TextView
                //handler.obtainMessage(MSG_LEER, bytes, -1, buffer).sendToTarget(); comentado*
                Thread.sleep(500);
            } catch (IOException e) {
                Log.e(TAG, "HiloConexion.run(): Error al realizar la lectura", e);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //******************************************************************************************
        public void cancelarConexion() {
            debug("HiloConexion.cancelarConexion()", "Iniciando metodo");
            try {
                // Forzamos el cierre del socket
                socket.close();

                // Cambiamos el estado del servicio
                setEstado(ESTADO_NINGUNO);
            }
            catch(IOException e) {
                Log.e(TAG, "HiloConexion.cerrarConexion(): Error al cerrar la conexion", e);
            }
        }

    }

    public void debug(String metodo, String msg)
    {
        if(DEBUG_MODE)
            Log.d(TAG, metodo + ": " + msg);
    }

    private UUID generarUUID()
    {
        ContentResolver appResolver = context.getApplicationContext().getContentResolver();
        String id = Secure.getString(appResolver, Secure.ANDROID_ID);
        final TelephonyManager tManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        final String deviceId = String.valueOf(tManager.getDeviceId());
        final String simSerialNumber = String.valueOf(tManager.getSimSerialNumber());
        final String androidId	= Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);

        UUID uuid = new UUID(androidId.hashCode(), ((long)deviceId.hashCode() << 32) | simSerialNumber.hashCode());
        uuid = new UUID((long)1000, (long)23);
        return uuid;
    }

} //termina clase BluetoothService

