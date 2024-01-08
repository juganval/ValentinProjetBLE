package com.jugavalentin.projetble.ui.scan

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter

import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.ActionMenuView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jugavalentin.projetble.ui.scan.data.BluetoothLEManager
import com.jugavalentin.projetble.ui.scan.data.Device
import com.jugavalentin.projetble.ui.scan.adapter.DeviceAdapter
import com.jugavalentin.projetble.LocalPreferences
import com.jugavalentin.projetble.R
import com.jugavalentin.projetble.ui.main.MainActivity

class ScanActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_LOCATION = 9999

    // Gestion du Bluetooth
    // L'Adapter permettant de se connecter
    private var bluetoothAdapter: BluetoothAdapter? = null

    // La connexion actuellement établie
    private var currentBluetoothGatt: BluetoothGatt? = null

    // « Interface système nous permettant de scanner »
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    // Parametrage du scan BLE
    private val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

    // On ne retourne que les « Devices » proposant le bon UUID
    private var scanFilters: List<ScanFilter> = arrayListOf(
      ScanFilter.Builder().setServiceUuid(ParcelUuid(BluetoothLEManager.DEVICE_UUID)).build()
    )

    // Variable de fonctionnement
    private var mScanning = false
    private val handler = Handler(Looper.getMainLooper())

    // DataSource de notre adapter.
    private val bleDevicesFoundList = arrayListOf<Device>()

    // Varibale privée pour le RecyclerView
    private var rvDevices: RecyclerView? = null
    private var startScan: Button? = null
    private var currentConnexion: TextView? = null
    private var disconnect: Button? = null
    private var toggleLed: Button? = null
    private var ledStatus: ImageView? = null
   // private var settings_device: ImageView? = null
    private var ledCount: TextView? = null
    private lateinit var scanMenuToolbar: ActionMenuView

    private val items = ArrayList<String>()


    @SuppressLint("MissingPermission", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        rvDevices = findViewById<RecyclerView>(R.id.rvDevices)
        startScan = findViewById<Button>(R.id.button_lancer_scan)
        currentConnexion = findViewById<TextView>(R.id.currentConnexion)
        disconnect = findViewById<Button>(R.id.button_deconnexion)
        toggleLed = findViewById<Button>(R.id.button_toggle_led)
        ledStatus = findViewById<ImageView>(R.id.ledStatus)
        //settings_device = findViewById<ImageView>(R.id.settings_device)
        ledCount = findViewById<TextView>(R.id.ledCount)

        scanMenuToolbar = findViewById<ActionMenuView>(R.id.scan_menu_toolbar)


        val topMenu = scanMenuToolbar.menu
        menuInflater.inflate(R.menu.scan_menu,topMenu)

        // Initialisation du RecyclerView
        setupRecycler()

        // Bouton pour lancer le scan
        findViewById<Button>(R.id.button_lancer_scan).setOnClickListener {
            askForPermission()
        }

        // Bouton pour se déconnecter
        findViewById<Button>(R.id.button_deconnexion).setOnClickListener {
            disconnectFromCurrentDevice()
        }

        // Bouton pour changer l'état de la LED
        findViewById<Button>(R.id.button_toggle_led).setOnClickListener {
            toggleLed()
        }

        // Menu de la toolbar (Scan, Changer nom périphérique)
        topMenu.getItem(0).setOnMenuItemClickListener() {
            // Appeler la bonne méthode
            val editText = EditText(this)

            val builder = MaterialAlertDialogBuilder(this)
                .setMessage(R.string.Changer_nom_peripherique)
                .setView(editText)
                .setPositiveButton(R.string.OK) { dialog, _ ->
                    val enteredText = editText.text.toString()
                    sendDeviceName(enteredText)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.Cancel) { dialog, _ ->

                    dialog.dismiss()
                }

            val dialog = builder.create()
            dialog.setCanceledOnTouchOutside(false)
            dialog.setCancelable(false)
            dialog.show()
            true
        }

        // Menu de la toolbar (Scan, Envoyer une animation)
        topMenu.getItem(1).setOnMenuItemClickListener() {
            val context = this

            // Création de la zone de texte pour l'animation
            val editText = EditText(context)
            editText.id = View.generateViewId()
            editText.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // Création du choix d'options prédéfinies
            val spinnerOptions = Spinner(context)
            spinnerOptions.id = View.generateViewId()
            spinnerOptions.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // Ajout d'options prédéfinies
            val optionsVue = arrayOf(getString(R.string.Nothing), getString(R.string.SOS), getString(
                R.string.epilepsy
            ))
            val options = arrayOf("0",getString(R.string.SOS_patern), getString(R.string.epilepsy_patern) )
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, optionsVue)
            spinnerOptions.adapter = adapter

            // Création d'un RelativeLayout
            val relativeLayout = RelativeLayout(context)
            relativeLayout.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            relativeLayout.addView(editText)
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            params.addRule(RelativeLayout.BELOW, editText.id)
            spinnerOptions.layoutParams = params
            relativeLayout.addView(spinnerOptions)

            // Construction du dialogue avec les choix (écriture ou choix prédéfini)
            val builder = MaterialAlertDialogBuilder(context)
                .setMessage(R.string.dialog_send_animation)
                .setView(relativeLayout)
                .setPositiveButton(R.string.OK) { dialog, _ ->
                    val enteredText = editText.text.toString()
                    val selectedOptionPosition = spinnerOptions.selectedItemPosition
                    if(enteredText.isNotBlank()){
                        sendAnimation(enteredText)
                    }
                    else if (selectedOptionPosition != 0){
                        //récupérer la place de l'option dans le tableau spinerOptions
                        val selectedOption = options[selectedOptionPosition].toString()
                        sendAnimation(selectedOption)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.Cancel) { dialog, _ ->
                    dialog.dismiss()
                }

            // Afficher le dialogue
            val dialog = builder.create()
            dialog.setCanceledOnTouchOutside(false)
            dialog.setCancelable(false)
            dialog.show()
            true
        }
    }

    /**
     * Méthode appelée lorsque l'application passe en arrière plan
     */
    override fun onResume() {
        super.onResume()

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            // Test si le téléphone est compatible BLE, si c'est pas le cas, on finish() l'activity
            // Afficher une boite de dialogue pour indiquer à l'utilisateur que le téléphone n'est pas compatible
            boiteDialog(getString(R.string.not_compatible))
            finish()
        }
    }

    /**
     * Affiche une boite de dialogue pour indiquer à l'utilisateur d'activer la localisation
     */
    private fun boiteDialogLocalisation(){
        val builder = MaterialAlertDialogBuilder(this)
            .setMessage(R.string.permission_refusee)
            .setPositiveButton(R.string.Accepter) { _, _ ->
                // L'utilisateur a accepté, essayez d'activer la localisation
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
            .setNegativeButton(R.string.Refuser) { _, _ ->
                // L'utilisateur a refusé, affichez un message approprié
                boiteDialog(getString(R.string.activer_la_localisation))
                startActivity(MainActivity.getStartedIntent(this, "REFUS-LOCALISATION"));
            }
            .setCancelable(false)  // Empêche de fermer la boîte de dialogue avec le bouton Retour
        builder.show()
    }

    /**
     * Méthode appelée lorsque l'utilisateur a répondu à la demande de permission.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && locationServiceEnabled()) {
                // Permission OK & service de localisation actif => Nous pouvons lancer l'initialisation du BLE.
                // En appelant la méthode setupBLE(), La méthode setupBLE() va initialiser le BluetoothAdapter et lancera le scan.
                setupBLE(false)
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED){
                // Permission KO
                // Afficher une boite de dialogue pour indiquer à l'utilisateur que la permission est refusée
                boiteDialog(getString(R.string.permission_refusee))

            } else if (!locationServiceEnabled() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Inviter à activer la localisation
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }

        }
    }

    /**
     * Permet de vérifier si l'application possede la permission « Localisation ». OBLIGATOIRE pour scanner en BLE
     * Sur Android 11, il faut la permission « BLUETOOTH_CONNECT » et « BLUETOOTH_SCAN »
     * Sur Android 10 et inférieur, il faut la permission « ACCESS_FINE_LOCATION » qui permet de scanner en BLE
     */
    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Demande de la permission (ou des permissions) à l'utilisateur.
     * Sur Android 11, il faut la permission « BLUETOOTH_CONNECT » et « BLUETOOTH_SCAN »
     * Sur Android 10 et inférieur, il faut la permission « ACCESS_FINE_LOCATION » qui permet de scanner en BLE
     */
    private fun askForPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_LOCATION)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN), PERMISSION_REQUEST_LOCATION)
        }
    }


    /**
     * Permet de vérifier si le service de localisation est activé sur le téléphone.
     */
    private fun locationServiceEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // This is new method provided in API 28
            val lm = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isLocationEnabled
        } else {
            // This is Deprecated in API 28
            val mode = Settings.Secure.getInt(this.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF)
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    val registerForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) {
            // Afficher une boite de dialogue pour indiquer à l'utilisateur que le bluetooth n'est pas connecté
            boiteDialog(getString(R.string.bluetooh_not_connected))
        }
    }


    /**
     * Récupération de l'adapter Bluetooth & vérification si celui-ci est actif.
     * Si il n'est pas actif, on demande à l'utilisateur de l'activer. Dans ce cas, au résultat le code présent dans « registerForResult » sera appelé.
     * Si il est déjà actif, on lance le scan.
     */
    @SuppressLint("MissingPermission")
    private fun setupBLE(boolean: Boolean) {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager?)?.let { bluetoothManager ->
            bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter != null && !bluetoothManager.adapter.isEnabled) {
                registerForResult.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else if (!boolean) {
                scanLeDevice()
            }
        }
    }

    /**
     * Méthode qui permet de scanner les périphériques BLE
     */
    @SuppressLint("MissingPermission")
    private fun scanLeDevice(scanPeriod: Long = 5000) {
        if (!mScanning) {
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

            // On vide la liste qui contient les devices actuellement trouvés
            bleDevicesFoundList.clear()

            //Rend non clickable le bouton de scan
            findViewById<Button>(R.id.button_lancer_scan).isClickable = false

            // Évite de scanner en double
            mScanning = true

            // On lance une tache qui durera « scanPeriod » à savoir donc de base
            // 5 secondes
            handler.postDelayed({
                mScanning = false
                bluetoothLeScanner?.stopScan(leScanCallback)

                //Rend clickable le bouton de scan et le recycler view
                findViewById<Button>(R.id.button_lancer_scan).isClickable = true

                // On affiche une boite de dialogue pour indiquer à l'utilisateur que le scan est terminé
                boiteDialog(getString(R.string.scan_ended))


            }, scanPeriod)

            // On lance le scan
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, leScanCallback)
        }
    }

    /**
     * Callback appelé à chaque fois qu'un device est trouvé
     */
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            // C'est ici que nous allons créer notre « Device » et l'ajouter dans la dataSource de notre RecyclerView

            val device = Device(result.device.name, result.device.address, result.device)
            if (!device.name.isNullOrBlank() && !bleDevicesFoundList.contains(device)) {
                bleDevicesFoundList.add(device)
            //    Indique à l'adapter que nous avons ajouté un élément, il va donc se mettre à jour
                findViewById<RecyclerView>(R.id.rvDevices).adapter?.notifyDataSetChanged()
            }
        }
    }

    /**
     * Méthode qui permet de se déconnecter du périphérique actuellement connecté
     */
    @SuppressLint("MissingPermission")
    private fun disconnectFromCurrentDevice() {
        currentBluetoothGatt?.disconnect()
        BluetoothLEManager.currentDevice = null
        setUiMode(false)
    }

    /**
     * Méthode qui permet d'afficher une boite de dialogue
     */
    private fun boiteDialog(string: String){
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.Message)
            .setMessage(string)
            .setPositiveButton(R.string.OK) { dialog, _ ->
                dialog.dismiss()
            }

        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.show()
    }

    /**
     * Initialisation du RecyclerView
     */
    private fun setupRecycler() {
        val rvDevice = findViewById<RecyclerView>(R.id.rvDevices) // Récupération du RecyclerView présent dans le layout
        rvDevice.layoutManager = LinearLayoutManager(this) // Définition du LayoutManager, Comment vont être affichés les éléments, ici en liste
        rvDevice.adapter = DeviceAdapter(bleDevicesFoundList) { device ->
            // Le code écrit ici sera appelé lorsque l'utilisateur cliquera sur un élément de la liste.
            // C'est un « callback », c'est-à-dire une méthode qui sera appelée à un moment précis.
            // Connections au périphérique bluetooth
            BluetoothLEManager.currentDevice = device.device
            connectToCurrentDevice()
        }
    }


    /**
     * Vérifier le SuppressLint (StringFormat Invalid)
     */
    @SuppressLint("MissingPermission", "StringFormatInvalid")
    private fun setUiMode(isConnected: Boolean) {
        if (isConnected) {
            // Connecté à un périphérique
            bleDevicesFoundList.clear()
            rvDevices?.visibility  = View.GONE
            startScan?.visibility = View.GONE
            currentConnexion?.visibility = View.VISIBLE
            currentConnexion?.text = getString(R.string.Connecte_a) + " " + BluetoothLEManager.currentDevice?.name
            disconnect?.visibility = View.VISIBLE
            toggleLed?.visibility = View.VISIBLE
            ledStatus?.visibility = View.VISIBLE
            ledStatus?.setImageResource(R.drawable.led_off)
            ledCount?.visibility = View.VISIBLE
            ledCount?.text = getString(R.string.led_count) + "  0"
            scanMenuToolbar?.visibility = View.VISIBLE
            //handleToggleLedNotificationUpdate(BluetoothLEManager.CHARACTERISTIC_NOTIFY_STATE)
        } else {
            // Non connecté, reset de la vue.
            rvDevices?.visibility = View.VISIBLE
            startScan?.visibility = View.VISIBLE
            ledStatus?.visibility = View.GONE
            currentConnexion?.visibility = View.GONE
            disconnect?.visibility = View.GONE
            toggleLed?.visibility = View.GONE
            ledCount?.visibility = View.GONE
            scanMenuToolbar?.visibility = View.GONE
        }
    }

    /**
     * Méthode qui permet de se connecté au périphérique sélectionné
     */
    @SuppressLint("MissingPermission")
    private fun connectToCurrentDevice() {
        BluetoothLEManager.currentDevice?.let { device ->


            Toast.makeText(this, "Connexion en cours … ${device.name}", Toast.LENGTH_SHORT).show()

            currentBluetoothGatt = device.connectGatt(
                this,
                false,
                BluetoothLEManager.GattCallback(
                    onConnect = {
                        // On indique à l'utilisateur que nous sommes correctement connecté
                        runOnUiThread {
                            // Nous sommes connecté au device, on active les notifications pour être notifié si la LED change d'état.
                            // À IMPLÉMENTER
                            // Vous devez appeler la méthode qui active les notifications BLE
                            enableListenBleNotify()

                            // On change la vue « pour être en mode connecté »
                            setUiMode(true)

                            // On sauvegarde dans les « LocalPréférence » de l'application le nom du dernier préphérique
                            // sur lequel nous nous sommes connecté
                            LocalPreferences.getInstance(this)

                            // À IMPLÉMENTER EN FONCTION DE CE QUE NOUS AVONS DIT ENSEMBLE
                        }
                    },
                    onNotify = {
                        runOnUiThread {
                            // VOUS DEVEZ APPELER ICI LA MÉTHODE QUI VA GÉRER LE CHANGEMENT D'ÉTAT DE LA LED DANS L'INTERFACE
                            // Si it (BluetoothGattCharacteristic) est pour l'UUID CHARACTERISTIC_NOTIFY_STATE
                            // Alors vous devez appeler la méthode qui va gérer le changement d'état de la LED
                            if (it.uuid == BluetoothLEManager.CHARACTERISTIC_NOTIFY_STATE) {
                                // À IMPLÉMENTER
                                handleToggleLedNotificationUpdate(it)
                            } else if (it.uuid == BluetoothLEManager.CHARACTERISTIC_GET_COUNT) {
                                // À IMPLÉMENTER
                                handleCountLedChangeNotificationUpdate(it)
                            } else if (it.uuid == BluetoothLEManager.CHARACTERISTIC_GET_WIFI_SCAN) {
                                // À IMPLÉMENTER
                                handleOnNotifyNotificationReceived(it)
                            }
                        }
                    },
                    onDisconnect = { runOnUiThread { disconnectFromCurrentDevice() } })
            )
        }
    }

    /**
     * Permet de récupérer le service principal du périphérique
     */
    private fun getMainDeviceService(): BluetoothGattService? {
        return currentBluetoothGatt?.let { bleGatt ->
            val service = bleGatt.getService(BluetoothLEManager.DEVICE_UUID)
            service?.let {
                return it
            } ?: run {

                // Afficher une boite de dialogue pour indiquer à l'utilisateur que le service n'a pas été trouvé
                boiteDialog(getString(R.string.uuid_not_found))

                return null;
            }
        } ?: run {
            // Afficher une boite de dialogue pour indiquer à l'utilisateur que le périphérique n'est pas connecté
            boiteDialog(getString(R.string.not_connected))

            return null
        }
    }

    /**
     * On change l'état de la LED (via l'UUID de toggle)
     */
    @SuppressLint("MissingPermission")
    private fun toggleLed() {
        getMainDeviceService()?.let { service ->
            val toggleLed = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_TOGGLE_LED_UUID)
            toggleLed.setValue("1")
            currentBluetoothGatt?.writeCharacteristic(toggleLed)
        }
    }

    /**
     * Méthode qui permet d'activer les notifications BLE
     */
    @SuppressLint("MissingPermission")
    private fun enableListenBleNotify() {
        getMainDeviceService()?.let { service ->

            // Afficher une boite de dialogue pour indiquer à l'utilisateur que les notifications sont activées
            boiteDialog(getString(R.string.enable_ble_notifications))

            //Toast.makeText(this, getString(R.string.enable_ble_notifications), Toast.LENGTH_SHORT).show()
            // Indique que le GATT Client va écouter les notifications sur le charactérisque
            val notificationStatus = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_NOTIFY_STATE)
            val notificationLedCount = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_GET_COUNT)
            val wifiScan = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_GET_WIFI_SCAN)

            currentBluetoothGatt?.setCharacteristicNotification(notificationStatus, true)
            currentBluetoothGatt?.setCharacteristicNotification(notificationLedCount, true)
            currentBluetoothGatt?.setCharacteristicNotification(wifiScan, true)
        }
    }

    /**
     * Méthode qui permet de gérer le changement d'état de la LED
     */
    private fun handleToggleLedNotificationUpdate(characteristic: BluetoothGattCharacteristic) {
        if (characteristic.getStringValue(0).equals("1", ignoreCase = true)) {
            ledStatus?.setImageResource(R.drawable.led_on)
        } else {
            ledStatus?.setImageResource(R.drawable.led_off)
        }
    }

    /**
     * Méthode qui permet de gérer le comptage des états de la LED
     */
    @SuppressLint("StringFormatInvalid")
    private fun handleCountLedChangeNotificationUpdate(characteristic: BluetoothGattCharacteristic) {
        characteristic.getStringValue(0).toIntOrNull()?.let {
            items.add("${items.size + 1}")
            ledCount?.text = getString(R.string.led_count) + " " + items.size
        }
    }

    /**
     * Méthode qui permet de gérer la réception des notifications pour les réseaux WiFi
     */
    private fun handleOnNotifyNotificationReceived(characteristic: BluetoothGattCharacteristic) {
        // TODO : Vous devez ici récupérer la liste des réseaux WiFi disponibles et les afficher dans une liste.
        // Vous pouvez utiliser un RecyclerView pour afficher la liste des réseaux WiFi disponibles.
        // aide moi à faire ça stp
        //Je souhaite afficher la liste des réseaux wifi disponibles dans le recycler view du layout activity_wifi
    }

    /**
     * Méthode qui permet d'envoyer une animation au périphérique (via l'UUID de toggle)
     */
    @SuppressLint("MissingPermission")
    private fun sendAnimation(string: String) {
        getMainDeviceService()?.let { service ->
            val toggleLed = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_TOGGLE_LED_UUID)
            toggleLed.setValue(string)
            currentBluetoothGatt?.writeCharacteristic(toggleLed)
        }
    }

    /**
     * Méthode qui permet de modifier le nom du périphérique
     */
    @SuppressLint("MissingPermission")
    private fun sendDeviceName(string: String) {
        getMainDeviceService()?.let { service ->
            val setDeviceName = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_SET_DEVICE_NAME)
            setDeviceName.setValue(string) // Le ESEO- est ajouté automatiquement
            currentBluetoothGatt?.writeCharacteristic(setDeviceName)
        }
    }

    companion object{
        fun getStartedIntent(context: Context): Intent {
            return Intent(context, ScanActivity::class.java)
        }
    }
}