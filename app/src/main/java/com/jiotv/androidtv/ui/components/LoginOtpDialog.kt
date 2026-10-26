package com.jiotv.androidtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.jiotv.androidtv.data.model.ServerConfig
import com.jiotv.androidtv.data.network.JioTvApiService
import com.jiotv.androidtv.data.network.ProxyStreamHandler
import com.jiotv.androidtv.ui.theme.FocusBorder
import com.jiotv.androidtv.ui.theme.JioBlue
import com.jiotv.androidtv.ui.theme.LiveGreen
import com.jiotv.androidtv.ui.theme.SurfaceDark
import com.jiotv.androidtv.ui.theme.SurfaceDarkVariant
import com.jiotv.androidtv.ui.theme.TextPrimary
import com.jiotv.androidtv.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginOtpDialog(
    serverConfig: ServerConfig,
    onDismiss: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val apiService = remember(serverConfig) {
        val proxyHandler = ProxyStreamHandler(serverConfig)
        JioTvApiService(proxyHandler, serverConfig)
    }

    var mobileNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) } // 1 = enter phone, 2 = enter otp
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, Color(0xFF2E323E), RoundedCornerShape(16.dp))
                .padding(28.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(JioBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (step == 1) Icons.Default.Phone else Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (step == 1) "Jio Account Login" else "Enter OTP Code",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDarkVariant)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (step == 1)
                        "Enter your 10-digit Jio mobile number to receive an authentication OTP."
                    else
                        "Enter the 6-digit OTP received on +91 $mobileNumber.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (step == 1) {
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = { if (it.length <= 10) mobileNumber = it.filter { c -> c.isDigit() } },
                        label = { Text("Jio Mobile Number", color = TextSecondary) },
                        prefix = { Text("+91 ", color = TextPrimary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JioBlue,
                            unfocusedBorderColor = Color(0xFF383C4A),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6) otpCode = it.filter { c -> c.isDigit() } },
                        label = { Text("6-Digit OTP", color = TextSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JioBlue,
                            unfocusedBorderColor = Color(0xFF383C4A),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = statusMessage.orEmpty(),
                        color = if (isError) Color(0xFFFF5252) else LiveGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step == 2) {
                        DialogActionButton(
                            label = "Back",
                            isPrimary = false,
                            onClick = {
                                step = 1
                                otpCode = ""
                                statusMessage = null
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    DialogActionButton(
                        label = if (isLoading) "Processing..." else if (step == 1) "Send OTP" else "Verify OTP",
                        isPrimary = true,
                        enabled = !isLoading && ((step == 1 && mobileNumber.length == 10) || (step == 2 && otpCode.length >= 4)),
                        onClick = {
                            if (step == 1) {
                                isLoading = true
                                isError = false
                                statusMessage = "Sending OTP to +91 $mobileNumber..."
                                scope.launch {
                                    val res = apiService.sendOtp(mobileNumber)
                                    isLoading = false
                                    if (res.isSuccess) {
                                        step = 2
                                        isError = false
                                        statusMessage = "OTP sent successfully! Please enter code."
                                    } else {
                                        isError = true
                                        statusMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to send OTP."
                                    }
                                }
                            } else {
                                isLoading = true
                                isError = false
                                statusMessage = "Verifying OTP code..."
                                scope.launch {
                                    val res = apiService.verifyOtp(mobileNumber, otpCode)
                                    isLoading = false
                                    if (res.isSuccess) {
                                        isError = false
                                        statusMessage = "Login successful!"
                                        onLoginSuccess(mobileNumber)
                                    } else {
                                        isError = true
                                        statusMessage = res.exceptionOrNull()?.localizedMessage ?: "Invalid OTP."
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DialogActionButton(
    label: String,
    isPrimary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        !enabled -> Color(0xFF2A2E39)
        isFocused -> FocusBorder
        isPrimary -> JioBlue
        else -> SurfaceDarkVariant
    }

    val txtColor = when {
        !enabled -> Color(0xFF6B7280)
        isFocused -> Color.Black
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = txtColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
