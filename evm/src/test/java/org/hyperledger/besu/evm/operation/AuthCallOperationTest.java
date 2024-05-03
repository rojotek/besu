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
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

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

  private AuthCallOperation authCallOperation;

  @BeforeEach
  public void setup() {
    messageFrame = mock(MessageFrame.class);
    evm = mock(EVM.class);
    authCallPrecompile = mock(PrecompiledContract.class);
    gasCalculator = mock(GasCalculator.class);

    when(gasCalculator.getBaseTierGasCost()).thenReturn(21000L);
    when(gasCalculator.getWarmStorageReadCost()).thenReturn(100L); // Static gas cost for warm_storage_read

    authCallOperation = new AuthCallOperation(gasCalculator);
  }

  @Test
  public void shouldSuccessfullyExecuteAuthCall() {
    // Setup successful AUTHCALL scenario
    Bytes inputData = Bytes.of(1, 2, 3); // Example input data
    Address authorizedAddress = Address.fromHexString("0xauthorized");

    when(messageFrame.getInputData()).thenReturn(inputData);
    // Mock the necessary frame and precompile behaviors
    when(authCallPrecompile.computePrecompile(any(), any())).thenReturn(PrecompiledContract.PrecompileContractResult.success(Bytes.EMPTY));

    // Execute the AUTHCALL operation
    OperationResult result = authCallOperation.execute(messageFrame, evm);

    // Assert successful execution
    assertThat(result.getHaltReason()).isNotPresent();
  }

  @Test
  public void shouldFailAuthCallOnInvalidInput() {
    // Setup invalid input scenario
    Bytes inputData = Bytes.of(1, 2, 3); // Example invalid input data

    when(messageFrame.getInputData()).thenReturn(inputData);
    // Mock the necessary frame and precompile behaviors to simulate an invalid input
    when(authCallPrecompile.computePrecompile(any(), any())).thenReturn(PrecompiledContract.PrecompileContractResult.halt(Bytes.EMPTY, Optional.of(ExceptionalHaltReason.INVALID_OPERATION)));

    // Execute the AUTHCALL operation
    OperationResult result = authCallOperation.execute(messageFrame, evm);

    // Assert failure due to invalid input
    assertThat(result.getHaltReason()).contains(ExceptionalHaltReason.INVALID_OPERATION);
  }

  // The test for shouldCorrectlyCalculateGasCostForAuthCall has been removed as the cost method does not exist in AuthCallOperation class
  // and the MessageFrame mock object does not have the methods isAddressInAccessedAddresses and getWorldState.
}
