import 'dotenv/config';

import { ethers } from "ethers";
import {
  getVerifyingPaymaster,
  getSimpleAccount,
  getGasFee,
  printOp,
  getHttpRpcClient,
  SEAPORT_ABI,
  ERC721_ABI,
} from "../../src";
import config from "../../config.json";
import { parseOpenSeaUrl } from '../../src/opensea.helper';
import { CNFTBuyer } from '../../src/opensea.helper';
import { OpenSeaSDK } from 'opensea-js';
import { sleep } from '../../src/utils';
import { conduitAddress, executorAddress, seaportAddress } from '../../src/consts';
import { SimpleAccountAPI } from '@account-abstraction/sdk';

const getOffer = async (seaport: OpenSeaSDK, tokenAddress: string, tokenId: string) => {
  const orders = await seaport.api.getOrders({
    side: "bid",
    assetContractAddress: tokenAddress,
    tokenIds: [tokenId],
    protocol: "seaport",
    orderBy: "eth_price",
  });

  if (!orders?.orders || orders.orders.length < 1) {
    console.log(`No offer found for token ${tokenId}`);
  }

  const order = orders.orders[0];

  await sleep(2);

  const fullfiller = await seaport.ethersProvider.getSigner().getAddress();
  console.log('Signer: ', fullfiller);

  const result = await seaport.api.generateFulfillmentData(
    fullfiller,
    `${order.orderHash}`,
    order.protocolAddress,
    order.side,
  )

  const signature = result.fulfillment_data.orders[0].signature;
  order.clientSignature = signature;
  order.protocolData.signature = signature;

  return order;
}

const erc721ApproveForAll = async (accountAPI: SimpleAccountAPI, provider: ethers.providers.JsonRpcProvider, erc721: ethers.Contract, address: string) => {
  const op1 = await accountAPI.createSignedUserOp({
    target: erc721.address,
    data: erc721.interface.encodeFunctionData("setApprovalForAll", [address, true]),
    ...(await getGasFee(provider)),
  });
  console.log(`Signed UserOperation: ${await printOp(op1)}`);

  const client = await getHttpRpcClient(
    provider,
    config.bundlerUrl,
    config.entryPoint
  );
  const uoHash = await client.sendUserOpToBundler(op1);
  console.log(`UserOpHash: ${uoHash}`);

  console.log("Waiting for transaction...");
  const txHash = await accountAPI.getUserOpReceipt(uoHash);
  console.log(`Transaction hash: ${txHash}`);
}

export default async function main(
  openSeaAssetUrl: string,
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

  const { tokenAddress, tokenId, network } = parseOpenSeaUrl(openSeaAssetUrl);

  const erc721 = new ethers.Contract(tokenAddress, ERC721_ABI, provider);
  const owner = await erc721.ownerOf(tokenId);

  const allowedForEntryPoint = await erc721.isApprovedForAll(owner, config.entryPoint);
  const allowedForSeaport = await erc721.isApprovedForAll(owner, seaportAddress);
  const allowedForExecutor = await erc721.isApprovedForAll(owner, executorAddress);
  const allowedForConduit = await erc721.isApprovedForAll(owner, conduitAddress);

  console.log(`Owner of token ${tokenId} is ${owner}`);

  console.log(`Allowed for entrypoint: `, allowedForEntryPoint);
  console.log(`Allowed for seaport: `, allowedForSeaport);
  console.log(`Allowed for executor: `, allowedForExecutor);
  console.log(`Allowed for conduit: `, allowedForConduit);

  const buyer = new CNFTBuyer(config.signingKey);
  const seaport = buyer.getSeaport(network);
  const order = await getOffer(seaport, tokenAddress, tokenId);  

  if (order) {
    if (!allowedForEntryPoint) {
      console.log('Approving entrypoint...');
      await erc721ApproveForAll(accountAPI, provider, erc721, config.entryPoint);
      console.log(`Allowed for entrypoint: `, await erc721.isApprovedForAll(owner, config.entryPoint));
    }

    if (!allowedForSeaport) {
      console.log('Approving seaport...');
      await erc721ApproveForAll(accountAPI, provider, erc721, seaportAddress);
      console.log(`Allowed for seaport: `, await erc721.isApprovedForAll(owner, seaportAddress));
    }

    if (!allowedForExecutor) {
      console.log('Approving executor...');
      await erc721ApproveForAll(accountAPI, provider, erc721, executorAddress);
      console.log(`Allowed for executor: `, await erc721.isApprovedForAll(owner, executorAddress));
    }

    if (!allowedForConduit) {
      console.log('Approving conduit...');
      await erc721ApproveForAll(accountAPI, provider, erc721, conduitAddress);
      console.log(`Allowed for conduit: `, await erc721.isApprovedForAll(owner, conduitAddress));
    }

    // Run seaport
    {
      const parameters = order.protocolData;

      const seaportAbi = new ethers.Contract(seaportAddress, SEAPORT_ABI, provider);

      console.log('Parameters: ', parameters);

      const op2 = await accountAPI.createSignedUserOp({
        target: seaportAbi.address,
        data: seaportAbi.interface.encodeFunctionData("fulfillOrder", [parameters, parameters.parameters.conduitKey]),
        // gasLimit: 1000000,
        ...(await getGasFee(provider)),
      });
      console.log(`Signed UserOperation: ${await printOp(op2)}`);

      const client = await getHttpRpcClient(
        provider,
        config.bundlerUrl,
        config.entryPoint
      );
      const uoHash = await client.sendUserOpToBundler(op2);
      console.log(`UserOpHash: ${uoHash}`);

      console.log("Waiting for transaction...");
      const txHash = await accountAPI.getUserOpReceipt(uoHash);
      console.log(`Transaction hash: ${txHash}`);
    }
  }
}
