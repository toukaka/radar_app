/*
 * Copyright 2024-2025 vschryabets@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.vsh.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jiangdg.demo.R

enum class AusbcScreen() {
    Start,
}

object DeviceListScreen {
    @Composable
    fun ProductItem(product: UsbDevice, onItemClick: (UsbDevice) -> Unit) {
        Column(modifier = Modifier
            .clickable { onItemClick(product) }
            .padding(8.dp)) {
            Text(
                text = "Vendor: ${product.vendorName}",
                fontSize = 18.sp
            )

        }
    }

    @Composable
    fun ScreenContent(
        uiState: DeviceListViewState,
        onReload: () -> Unit,
        onSelectUsbDevice: (UsbDevice) -> Unit
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.align(Alignment.End)
            ) {
                Button(
                    modifier = Modifier.padding(start = 16.dp),
                    onClick = onReload
                ) {
                    Text("Reload USB devices")
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                this.items(uiState.devices) {
                    ProductItem(product = it, onItemClick = onSelectUsbDevice)
                }
            }
        }
    }

}

@Composable
fun AusbcApp(
    viewModel: DeviceListViewModel,
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
    ) { innerPadding ->
        val uiState by viewModel.state.collectAsState()

        NavHost(
            navController = navController,
            startDestination = AusbcScreen.Start.name,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(route = AusbcScreen.Start.name) {
                DeviceListScreen.ScreenContent(
                    uiState = uiState,
                    onReload = { viewModel.onEnumerate() },
                    onSelectUsbDevice = { viewModel.onClick(it) })
            }
        }
    }
}
