use anchor_lang::prelude::*;

pub mod constants;
pub mod errors;
pub mod events;
pub mod state;

declare_id!("3ofJTv8xX68aFTQJLNZV2tDXESPuCjcfRHpeLd37djqk");

#[program]
pub mod tally {}
