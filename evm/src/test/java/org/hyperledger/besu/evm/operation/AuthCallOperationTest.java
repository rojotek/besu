package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.datatypes.Address;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthCallOperationTest {

  @Mock private MessageFrame messageFrame;
  @Mock private EVM evm;
  @Mock private PrecompiledContract authCallPrecompile;
  @Mock private GasCalculator gasCalculator;
  private NoOpMetricsSystem metricsSystem;

  private AuthCallOperation authCallOperation;

  @BeforeEach
  public void setup() {
    messageFrame = mock(MessageFrame.class);
    evm = mock(EVM.class);
    authCallPrecompile = mock(PrecompiledContract.class);
    gasCalculator = mock(GasCalculator.class);
    metricsSystem = new NoOpMetricsSystem();

    when(gasCalculator.getBaseTierGasCost()).thenReturn(21000L);
    when(gasCalculator.getWarmStorageReadCost()).thenReturn(100L); // Static gas cost for warm_storage_read

    authCallOperation = new AuthCallOperation(gasCalculator, metricsSystem);
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
    // Dynamic gas cost components
    int coldAccountAccessCost = messageFrame.isAddressInAccessedAddresses(authorizedAddress) ? 0 : 2600; // From EIP-2929, only if address not accessed
    int valueTransferCost = messageFrame.getValue().isZero() ? 0 : 9000; // Additional cost if value > 0, from EIP-7
    int emptyAddressCost = messageFrame.getWorldState().get(authorizedAddress).isEmpty() ? 25000 : 0; // Additional cost if address is empty, from EIP-2
    // Calculate the total expected gas cost
    long expectedGasCost = 100L + coldAccountAccessCost + valueTransferCost + emptyAddressCost;
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
    // Dynamic gas cost components
    int coldAccountAccessCost = messageFrame.isAddressInAccessedAddresses(authorizedAddress) ? 0 : 2600; // From EIP-2929, only if address not accessed
    int valueTransferCost = messageFrame.getValue().isZero() ? 0 : 9000; // Additional cost if value > 0, from EIP-7
    int emptyAddressCost = messageFrame.getWorldState().get(authorizedAddress).isEmpty() ? 25000 : 0; // Additional cost if address is empty, from EIP-2
    // Calculate the total expected gas cost
    long expectedGasCost = 100L + coldAccountAccessCost + valueTransferCost + emptyAddressCost;
    assertThat(result.getGasCost()).contains(expectedGasCost);
  }

  @Test
  public void shouldCorrectlyCalculateGasCostForAuthCall() {
    // Setup scenario for gas cost calculation
    // Mock the necessary frame behaviors
    Bytes inputData = Bytes.of(1, 2, 3); // Example input data

    when(messageFrame.getInputData()).thenReturn(inputData);

    // Calculate gas cost
    long cost = authCallOperation.cost(messageFrame);

    // Assert correct gas cost calculation as per EIP-3074
    // Dynamic gas cost components
    int coldAccountAccessCost = messageFrame.isAddressInAccessedAddresses(authorizedAddress) ? 0 : 2600; // From EIP-2929, only if address not accessed
    int valueTransferCost = messageFrame.getValue().isZero() ? 0 : 9000; // Additional cost if value > 0, from EIP-7
    int emptyAddressCost = messageFrame.getWorldState().get(authorizedAddress).isEmpty() ? 25000 : 0; // Additional cost if address is empty, from EIP-2
    // Calculate the total expected gas cost
    long expectedGasCost = 100L + coldAccountAccessCost + valueTransferCost + emptyAddressCost;
    assertThat(cost).isEqualTo(expectedGasCost);
  }
}
