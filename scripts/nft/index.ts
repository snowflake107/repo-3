import { Command } from "commander";
import transfer from './transfer';

const program = new Command();

program
  .name("ERC-721 Functions")
  .description(
    "A collection of example scripts for working with ERC-721"
  )
  .version("0.1.0");

program
  .command("erc721Transfer")
  .description("Transfer ERC-721 token")
  .requiredOption("-tkn, --token <address>", "The token address")
  .requiredOption("-id, --tokenId <number>", "Token id to transfer")
  .requiredOption("-t, --to <address>", "The recipient address")
  .action(async (opts) => {
    transfer(
      opts.token,
      opts.tokenId,
      opts.to,
    )
  });

program.parse();
