package org.hyperledger.besu.evm.operation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract.PrecompileContractResult;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Optional;

public class AuthOperationTest {

  @Mock private MessageFrame messageFrame;
  @Mock private EVM evm;
  @Mock private PrecompiledContract authPrecompile;
  @Mock private GasCalculator gasCalculator;
  @Mock private PrecompileContractRegistry precompileContractRegistry;

  private AuthOperation authOperation;

  @BeforeEach
  public void setup() {
    messageFrame = mock(MessageFrame.class);
    evm = mock(EVM.class);
    authPrecompile = mock(PrecompiledContract.class);
    gasCalculator = mock(GasCalculator.class);
    precompileContractRegistry = mock(PrecompileContractRegistry.class);

    when(gasCalculator.getBaseTierGasCost()).thenReturn(21000L);

    authOperation = new AuthOperation(gasCalculator, precompileContractRegistry);
  }

  @Test
  public void shouldSuccessfullyAuthorize() {
    // Setup successful authorization scenario
    // Mock the necessary frame and precompile behaviors
    // Assume the signature and message are valid and the precompile returns a successful result
    Bytes signature = Bytes.of(1, 2, 3); // Example signature
    Bytes message = Bytes.of(1, 2, 3); // Example message
    Address authorizedAddress = Address.fromHexString("0xauthorized");

    when(messageFrame.getInputData()).thenReturn(Bytes.concatenate(message, signature));
    when(authPrecompile.computePrecompile(any(), eq(messageFrame))).thenReturn(PrecompileContractResult.success(Bytes.EMPTY));

    // Execute the AUTH operation
    OperationResult result = authOperation.execute(messageFrame, evm);

    // Assert successful authorization
    // Check that the authorized address is set correctly in the message frame
    assertThat(messageFrame.getStackItem(0)).isEqualTo(authorizedAddress); // Assuming the authorized address is pushed to the stack
    assertThat(result.getHaltReason()).isNotPresent();
    // Assert correct gas cost for successful authorization as per EIP-3074
    assertThat(result.getGasCost()).isEqualTo(3000L);
  }

  @Test
  public void shouldFailAuthorizationOnInvalidSignature() {
    // Setup invalid signature scenario
    // Mock the necessary frame and precompile behaviors to simulate an invalid signature
    Bytes signature = Bytes.of(1, 2, 3); // Example invalid signature
    Bytes message = Bytes.of(1, 2, 3); // Example message

    when(messageFrame.getInputData()).thenReturn(Bytes.concatenate(message, signature));
    when(authPrecompile.computePrecompile(any(), eq(messageFrame))).thenReturn(PrecompileContractResult.halt(Bytes.EMPTY, Optional.of(ExceptionalHaltReason.INVALID_OPERATION)));

    // Execute the AUTH operation
    OperationResult result = authOperation.execute(messageFrame, evm);

    // Assert failure due to invalid signature
    // Check that the message frame does not set an authorized address
    assertThat(messageFrame.getStackItem(0)).isNull();
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_OPERATION);
    assertThat(result.getGasCost()).isEqualTo(21000L);
  }

  @Test
  public void shouldExceptionallyHaltOnPrecompileNotDefined() {
    // Setup scenario where precompile is not defined
    when(authPrecompile.computePrecompile(any(), eq(messageFrame))).thenReturn(PrecompileContractResult.halt(Bytes.EMPTY, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR)));

    // Execute the AUTH operation
    OperationResult result = authOperation.execute(messageFrame, evm);

    // Assert exceptional halt
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.PRECOMPILE_ERROR);
  }

  @Test
  public void shouldCorrectlyCalculateGasCost() {
    // Setup scenario for gas cost calculation
    // Mock the necessary frame behaviors
    Bytes signature = Bytes.of(1, 2, 3); // Example signature
    Bytes message = Bytes.of(1, 2, 3); // Example message

    when(messageFrame.getInputData()).thenReturn(Bytes.concatenate(message, signature));

    // Calculate gas cost
    long cost = authOperation.cost(messageFrame);

    // Assert correct gas cost calculation as per EIP-3074
    assertThat(cost).isEqualTo(3000L);
  }
}
