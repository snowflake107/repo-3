import { ethers } from "ethers";
import {
  ERC721_ABI,
} from "../../src";
// @ts-ignore
import config from "../../config.json";

export default async function main(
  address: string,
  tokenId: string,
  recipient: string,
) {
  const provider = new ethers.providers.JsonRpcProvider(config.rpcEndpoint);

  const signer = new ethers.Wallet(config.walletKey, provider);

  const tokenAddress = ethers.utils.getAddress(address);
  const erc721 = new ethers.Contract(tokenAddress, ERC721_ABI, provider);
  const owner = await erc721.ownerOf(tokenId);

  console.log(`Owner of token ${tokenId} is ${owner}`);

  console.log(`Signer is ${signer.address}`);

  if (owner !== signer.address) {
    throw new Error("Signer is not the owner of the token");
  }

  const tx = await erc721.connect(signer).transferFrom(owner, recipient, tokenId);
  console.log(`Transaction hash: ${tx.hash}`);

  const receipt = await tx.wait();

  console.log(`Transaction receipt status: `, receipt.status);

}
