import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
