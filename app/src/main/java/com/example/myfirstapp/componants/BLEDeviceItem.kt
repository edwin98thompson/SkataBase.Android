import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myfirstapp.model.BLEDevice

@Composable
fun BLEDeviceItem(device: BLEDevice, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(
            text = device.name ?: "Unknown",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = device.address,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
