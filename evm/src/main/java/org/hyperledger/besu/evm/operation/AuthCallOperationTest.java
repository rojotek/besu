package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Bytes;
import org.hyperledger.besu.datatypes.Gas;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompileContract;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.metrics.OperationTimer;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthCallOperationTest {

  @Mock private MessageFrame messageFrame;
  @Mock private EVM evm;
  @Mock private PrecompileContract authCallPrecompile;
  @Mock private PrecompileContractRegistry precompileContractRegistry;
  @Mock private GasCalculator gasCalculator;
  @Mock private MetricsSystem metricsSystem;
  @Mock private OperationTimer operationTimer;

  private AuthCallOperation authCallOperation;

  @Before
  public void setup() {
    messageFrame = mock(MessageFrame.class);
    evm = mock(EVM.class);
    authCallPrecompile = mock(PrecompileContract.class);
    precompileContractRegistry = mock(PrecompileContractRegistry.class);
    gasCalculator = mock(GasCalculator.class);
    metricsSystem = mock(MetricsSystem.class);
    operationTimer = mock(OperationTimer.class);

    when(metricsSystem.createTimer("EVM", "AUTHCALL")).thenReturn(operationTimer);
    when(precompileContractRegistry.get(any())).thenReturn(Optional.of(authCallPrecompile));
    when(gasCalculator.getBaseTierGasCost()).thenReturn(Gas.of(21000));
    when(gasCalculator.getWarmStorageReadCost()).thenReturn(Gas.of(100)); // Static gas cost for warm_storage_read

    authCallOperation = new AuthCallOperation(gasCalculator, precompileContractRegistry, metricsSystem);
  }

  @Test
  public void shouldSuccessfullyExecuteAuthCall() {
    // Setup successful AUTHCALL scenario
    // Mock the necessary frame and precompile behaviors
    // Assume the signature and message are valid and the precompile returns a successful result
    Bytes inputData = Bytes.of(1, 2, 3); // Example input data
    Address authorizedAddress = Address.fromHexString("0xauthorized");

    when(messageFrame.getInputData()).thenReturn(inputData);
    when(authCallPrecompile.compute(any(), any(), any())).thenReturn(authorizedAddress);

    // Execute the AUTHCALL operation
    OperationResult result = authCallOperation.execute(messageFrame, evm);

    // Assert successful execution
    // Check that the authorized address is set correctly in the message frame
    assertThat(messageFrame.getAuthorizedAddress()).isEqualTo(authorizedAddress);
    assertThat(result.getHaltReason()).isEmpty();
    // Assert correct gas cost for successful AUTHCALL as per EIP-3074
    // Dynamic gas cost components
    int coldAccountAccessCost = 2600; // From EIP-2929
    int valueTransferCost = 0; // Additional cost if value > 0
    int emptyAddressCost = 0; // Additional cost if address is empty
    // Assuming for test purposes that the address is not in the accessed addresses set and value is 0
    int dynamicGasCost = coldAccountAccessCost - gasCalculator.getWarmStorageReadCost().toInt(); // cold_account_access_cost - warm_storage_read_cost
    // Assuming for test purposes that the address is not empty
    // Calculate the total expected gas cost
    Gas expectedGasCost = Gas.of(gasCalculator.getWarmStorageReadCost().toInt() + dynamicGasCost + valueTransferCost + emptyAddressCost);
    assertThat(result.getGasCost()).contains(expectedGasCost);
  }

  @Test
  public void shouldFailAuthCallOnInvalidInput() {
    // Setup invalid input scenario
    // Mock the necessary frame and precompile behaviors to simulate an invalid input
    Bytes inputData = Bytes.of(1, 2, 3); // Example invalid input data

    when(messageFrame.getInputData()).thenReturn(inputData);
    when(authCallPrecompile.compute(any(), any(), any())).thenReturn(Bytes.EMPTY);

    // Execute the AUTHCALL operation
    OperationResult result = authCallOperation.execute(messageFrame, evm);

    // Assert failure due to invalid input
    // Check that the message frame does not set an authorized address
    assertThat(messageFrame.getAuthorizedAddress()).isNull();
    assertThat(result.getHaltReason()).contains(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    // Assert correct gas cost for failed AUTHCALL as per EIP-3074
    // Dynamic gas cost components
    int coldAccountAccessCost = 2600; // From EIP-2929
    int valueTransferCost = 0; // Additional cost if value > 0
    int emptyAddressCost = 0; // Additional cost if address is empty
    // Assuming for test purposes that the address is not in the accessed addresses set and value is 0
    int dynamicGasCost = coldAccountAccessCost - gasCalculator.getWarmStorageReadCost().toInt(); // cold_account_access_cost - warm_storage_read_cost
    // Assuming for test purposes that the address is not empty
    // Calculate the total expected gas cost
    Gas expectedGasCost = Gas.of(gasCalculator.getWarmStorageReadCost().toInt() + dynamicGasCost + valueTransferCost + emptyAddressCost);
    assertThat(result.getGasCost()).contains(expectedGasCost);
  }

  @Test
  public void shouldCorrectlyCalculateGasCostForAuthCall() {
    // Setup scenario for gas cost calculation
    // Mock the necessary frame behaviors
    Bytes inputData = Bytes.of(1, 2, 3); // Example input data

    when(messageFrame.getInputData()).thenReturn(inputData);

    // Calculate gas cost
    Gas cost = authCallOperation.cost(messageFrame);

    // Assert correct gas cost calculation as per EIP-3074
    // Dynamic gas cost components
    int coldAccountAccessCost = 2600; // From EIP-2929
    int valueTransferCost = 0; // Additional cost if value > 0
    int emptyAddressCost = 0; // Additional cost if address is empty
    // Assuming for test purposes that the address is not in the accessed addresses set and value is 0
    int dynamicGasCost = coldAccountAccessCost - gasCalculator.getWarmStorageReadCost().toInt(); // cold_account_access_cost - warm_storage_read_cost
    // Assuming for test purposes that the address is not empty
    // Calculate the total expected gas cost
    Gas expectedGasCost = Gas.of(gasCalculator.getWarmStorageReadCost().toInt() + dynamicGasCost + valueTransferCost + emptyAddressCost);
    assertThat(cost).isEqualTo(expectedGasCost);
  }
}
