package net.marllex.waselak.feature.manager.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.dashboard.generated.resources.Res
import net.marllex.waselak.feature.manager.dashboard.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.ui.components.ChannelChip
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.components.OrderStatusChip
import net.marllex.waselak.core.common.utils.CurrencyFormatter
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BrandedTopBar(uiState: DashboardViewModel.UiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp // Gives it that modern "rebranded" lift
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding() // Handles the clock/battery area
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- 1. THE LOGO ---
            val logoUrl = uiState.vendor?.logoUrl
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (!logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Rounded.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // --- 2. THE IDENTITY COLUMN ---
            Column(
                modifier = Modifier.weight(1f) // This ensures the text stays in its lane
            ) {
                Text(
                    text = uiState.vendor?.name ?: "Our Store",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                uiState.userName?.let { name ->
                    Text(
                        text = stringResource(Res.string.welcome_message, name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // --- 3. OPTIONAL ACTION (e.g. Profile or Search) ---
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Rounded.NotificationsNone, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernDashboardScreen(
    viewModel: DashboardViewModel = koinViewModel(),
    onNavigateToChatbot: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Auto-refresh when screen becomes visible
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadDashboard()
        }
    }

    Scaffold(
        topBar = {
            BrandedTopBar(uiState)
        },
        floatingActionButton = {
//            FloatingActionButton(
//                onClick = onNavigateToChatbot,
//                containerColor = MaterialTheme.colorScheme.primary,
//                contentColor = MaterialTheme.colorScheme.onPrimary
//            ) {
//                Row(
//                    modifier = Modifier.padding(horizontal = 16.dp),
//                    horizontalArrangement = Arrangement.spacedBy(8.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Icon(
//                        imageVector = Icons.Filled.Chat,
//                        contentDescription = stringResource(Res.string.ai_assistant)
//                    )
//                    Text(
//                        text = stringResource(Res.string.ai_assistant),
//                        style = MaterialTheme.typography.labelLarge
//                    )
//                }
//            }
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.recentOrders.isEmpty() -> LoadingIndicator()
            uiState.error != null && uiState.recentOrders.isEmpty() -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadDashboard,
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Quick Stats Section
                item {
                    Text(
                        text = stringResource(Res.string.quick_overview),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Horizontal scrolling stats cards
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), // Increased spacing for a cleaner look
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp) // Proper breathing room
                    ) {
                        // 1. ACTIVE ORDERS - High Priority (Purple/Indigo)
                        item {
                            ModernStatCard(
                                title = stringResource(Res.string.active_orders),
                                value = uiState.activeOrdersCount.toString(),
                                icon = Icons.Default.PendingActions, // More modern icon
                                gradient = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
                                subtitle = "Needs Attention"
                            )
                        }

                        // 2. COMPLETED ORDERS - Success (Emerald/Green)
                        item {
                            ModernStatCard(
                                title = "Daily Total", // Changed from Today's Orders to differentiate
                                value = uiState.todayOrdersCount.toString(),
                                icon = Icons.Default.CheckCircle,
                                gradient = listOf(Color(0xFF10B981), Color(0xFF34D399)),
                                subtitle = "Total Processed"
                            )
                        }

                        // 3. PERFORMANCE/SPEED - (Optional but great for UX)
                        item {
                            ModernStatCard(
                                title = "Completed",
                                value = uiState.completedOrdersCount.toString(),
                                icon = Icons.Default.FactCheck,
                                gradient = listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)), // Cyan to Blue
                                subtitle = "Ready for pickup"
                            )
                        }

                        // 4. REVENUE - Crucial Metric (Amber/Orange)
                        item {
                            // Using a simple currency formatter for better UX
                            val formattedRevenue = "${uiState.todayRevenue.toInt()}"
                            ModernStatCard(
                                title = stringResource(Res.string.today_s_revenue),
                                value = formattedRevenue,
                                icon = Icons.Default.Payments,
                                gradient = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
                                subtitle = "EGP Total",
                            )
                        }
                    }
                }

                // Stock Overview Section
                item {
                    Text(
                        text = stringResource(Res.string.stock_overview),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CompactStatCard(
                            title = stringResource(Res.string.total_items),
                            value = uiState.totalStockItems.toString(),
                            icon = Icons.Filled.Inventory,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        
                        val hasAlerts = uiState.lowStockCount > 0 || uiState.outOfStockCount > 0
                        CompactStatCard(
                            title = stringResource(Res.string.stock_alerts),
                            value = "${uiState.lowStockCount + uiState.outOfStockCount}",
                            icon = Icons.Filled.Warning,
                            color = if (hasAlerts) Color(0xFFEF4444) else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                            subtitle = if (hasAlerts) "${uiState.outOfStockCount} ${stringResource(Res.string.critical)}" else null
                        )
                    }
                }

                // Recent Orders Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.recent_orders),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${uiState.recentOrders.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                items(uiState.recentOrders.take(10), key = { it.id }) { order ->
                    ModernOrderCard(order = order)
                }
            }
        }
    }
}

@Composable
private fun ModernStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    gradient: List<Color>,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .width(180.dp)
            .height(160.dp), // Increased height for better padding
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        // Subtle border instead of flat elevation
        border = BorderStroke(1.dp, gradient[0].copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            gradient[0].copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header: Icon and Label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(gradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Body: Value and Trend
                Column {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp // Modern tight lettering
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Optional: Add a tiny up/down arrow icon here later
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = gradient[0], // Use the primary gradient color for the "trend"
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ModernOrderCard(order: Order) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Receipt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "#${order.id.takeLast(6).uppercase()}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        order.clientName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = CurrencyFormatter.format(order.total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OrderStatusChip(status = order.status)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelChip(channel = order.channel.name)
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${order.items.size} ${stringResource(Res.string.items_label)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
