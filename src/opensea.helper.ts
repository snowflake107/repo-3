import * as dotenv from 'dotenv';
dotenv.config();

import * as opensea from 'opensea-js';
const OpenSeaPort = opensea.OpenSeaPort;

import HDWalletProvider from '@truffle/hdwallet-provider';
import { OpenSeaAPIConfig } from 'opensea-js/lib/types';
import { sleep } from './utils';

import config from "../config.json";

export const ETH_NUMBER = 1_000_000_000_000_000_000;

export function parseOpenSeaUrl(url: string) {
  const matcher = /([a-z]*)\/(0x[^/]*)\/([0-9]*)/;
  const match = matcher.exec(url) || ['',''];
  let network = opensea.Network.Main;
  if (match[1] === 'rinkeby') {
    network = opensea.Network.Rinkeby;
  }
  if (match[1] === 'goerli') {
    network = opensea.Network.Goerli;
  }
  return { tokenAddress: match[2], tokenId: match[3], network };
}

export function logger(...args: any[]) {
  console.log(new Date().toTimeString(), ...args);
}

export class CNFTBuyer {
  private walletPrivateKey: string;
  // eslint-disable-next-line @typescript-eslint/no-inferrable-types
  private extraGas: number = 0;

  constructor(walletPrivateKey: string, extraGas = 0) {
    this.walletPrivateKey = walletPrivateKey;
    this.extraGas = extraGas;
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  private getProvider(network: opensea.Network) {
    const _provider = new HDWalletProvider({
      privateKeys: [this.walletPrivateKey],
      providerOrUrl: config.rpcUrl,
    })
    return _provider;
  }

  public getSeaport(network: opensea.Network) {
    const _provider = this.getProvider(network);
    const apiConfig: OpenSeaAPIConfig = {
      networkName: network,
    };
    if (network === opensea.Network.Main) {
      // apiConfig.apiKey = config.openseaKey;
    }
    const seaport = new OpenSeaPort(_provider, apiConfig, (arg) => console.log(arg));
    seaport.gasIncreaseFactor = 1.4;
    return seaport;
  }

  public async getOrderV14(openSeaAssetUrl: string, fullfiller: string) {
    const { tokenAddress: assetContractAddress, tokenId, network } = parseOpenSeaUrl(openSeaAssetUrl);

    try {
      logger('Get Order V1.4...');

      await sleep(0.5);
      const seaport = this.getSeaport(network);
      const order = await seaport.api.getOrder({ // extracting order to fulfill
        assetContractAddress,
        tokenIds: [tokenId],
        side: 'ask',
        protocol: 'seaport',
      });
      console.log('Protocol: ', order.protocolAddress);

      if (order.orderHash) {
        await sleep(2);
        const result = await seaport.api.generateFulfillmentData(
          fullfiller,
          order.orderHash,
          order.protocolAddress,
          order.side,
        )
        const signature = result.fulfillment_data.orders[0].signature;
        order.clientSignature = signature;
        order.protocolData.signature = signature;
        logger('Get Order V1.4. Done.');
      } else {
        logger('Get Order V1.4 failed. Order hash is empty.');
      }
      return order;
    } catch (e) {
      //
      logger('Get Order V1.4 failed.');
      logger(e);
    }

    return null;
  }
}