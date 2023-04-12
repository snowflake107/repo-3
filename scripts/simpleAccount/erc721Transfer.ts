import { ethers } from "ethers";
import {
  getVerifyingPaymaster,
  getSimpleAccount,
  getGasFee,
  printOp,
  getHttpRpcClient,
  ERC721_ABI,
} from "../../src";
import config from "../../config.json";

export default async function main(
  address: string,
  tokenId: string,
  recipient: string,
  withPM: boolean
) {
  const provider = new ethers.providers.JsonRpcProvider(config.rpcUrl);
  const paymasterAPI = withPM
    ? getVerifyingPaymaster(config.paymasterUrl, config.entryPoint)
    : undefined;

  const accountAPI = getSimpleAccount(
    provider,
    config.signingKey,
    config.entryPoint,
    config.simpleAccountFactory,
    paymasterAPI
  );

  const signer =  await accountAPI.getAccountAddress();

  console.log(`Signer is ${signer}`);

  const tokenAddress = ethers.utils.getAddress(address);
  const erc721 = new ethers.Contract(tokenAddress, ERC721_ABI, provider);
  const owner = await erc721.ownerOf(tokenId);

  console.log(`Owner of token ${tokenId} is ${owner}`);

  if (owner !== signer) {
    throw new Error("Signer is not the owner of the token");
  }

  const op = await accountAPI.createSignedUserOp({
    target: erc721.address,
    data: erc721.interface.encodeFunctionData("transferFrom", [owner, recipient, tokenId]),
    ...(await getGasFee(provider)),
  });
  console.log(`Signed UserOperation: ${await printOp(op)}`);

  const client = await getHttpRpcClient(
    provider,
    config.bundlerUrl,
    config.entryPoint
  );
  const uoHash = await client.sendUserOpToBundler(op);
  console.log(`UserOpHash: ${uoHash}`);

  console.log("Waiting for transaction...");
  const txHash = await accountAPI.getUserOpReceipt(uoHash);
  console.log(`Transaction hash: ${txHash}`);
}
