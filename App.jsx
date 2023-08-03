import React, { useEffect, useCallback, useState } from 'react';
import {
  StyleSheet,
  View,
  Text,
  NativeEventEmitter,
  NativeModules,
} from 'react-native';

const { ZebraScanner } = NativeModules;
const ZebraScannerEmitter = new NativeEventEmitter(ZebraScanner);

const App = () => {
  const [data, setData] = useState("");
  const [readerRunning, setReaderRunning] = useState(false);

  useEffect(() => {
    ZebraScannerEmitter.addListener(ZebraScanner.BARCODE_READ_SUCCESS, (data) => {
      console.log('Barcode scan read: ' + data.data);
      setData(data.data);
    });

    ZebraScannerEmitter.addListener(ZebraScanner.BARCODE_READ_FAIL, (data) => {
      console.error('Failed to read barcode: ' + data.data);
    });

    return () => {
      ZebraScannerEmitter.removeAllListeners(ZebraScanner.BARCODE_READ_SUCCESS);
      ZebraScannerEmitter.removeAllListeners(ZebraScanner.BARCODE_READ_FAIL);
    }
  }, []);

  const startReader = useCallback(() => {

    ZebraScanner.startReader();

  }
  )

  const stopReader = useCallback(() => {

    ZebraScanner.stopReader();

  }
  );

  return (
    <View style={styles.container}>
      <Text style={styles.text} onPress={startReader}>
        Iniciar Lector
      </Text>
      <Text style={styles.text}>o</Text>
      <Text style={styles.text} onPress={stopReader}>
        Detener Lector
      </Text>
      <Text style={styles.text}> Esta es mi data: {data}</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  text: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
});

export default App;
